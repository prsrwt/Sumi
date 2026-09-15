package com.sumi.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * The one place that translates between stored rows and domain types. Epoch
 * millis and enum-name strings never leak past this class.
 */
class SumiRepository(private val db: SumiDatabase) {

    private val dao = db.dao()

    // ---- goals ----

    fun observeGoals(): Flow<List<Goal>> =
        dao.observeGoals().map { rows -> rows.map { it.toGoal() } }

    suspend fun goalsNow(): List<Goal> = dao.getGoals().map { it.toGoal() }

    /**
     * Goal names appear on every row of the sheet, so a real rename queues every
     * month for rewriting. Re-saving the same name, which the debounced fields do
     * all the time, changes nothing and queues nothing.
     */
    suspend fun setGoalName(slot: Int, name: String) {
        require(slot in 0 until GOAL_COUNT) { "slot out of range: $slot" }
        db.withTransaction {
            if (dao.setGoalName(slot, name.trim()) > 0) markEveryMonthDirty()
        }
    }

    suspend fun assignElement(slot: Int, element: Element) {
        require(slot in 0 until GOAL_COUNT) { "slot out of range: $slot" }
        db.withTransaction {
            dao.assignElement(slot, element.name)
            markEveryMonthDirty()
        }
    }

    // ---- settings ----

    fun observeSettings(): Flow<Settings> =
        dao.observeSettings().map { it?.toSettings() ?: Settings.Default }

    suspend fun settingsNow(): Settings = dao.getSettings()?.toSettings() ?: Settings.Default

    suspend fun saveSettings(settings: Settings) = dao.putSettings(
        SettingsEntity(
            id = 0,
            askIntervalMinutes = settings.askInterval.toMinutes().toInt(),
            quietStartMinute = settings.quietStart.toSecondOfDay() / 60,
            quietEndMinute = settings.quietEnd.toSecondOfDay() / 60
        )
    )

    // ---- the Google Sheets link ----

    fun observeSheetsLink(): Flow<SheetsLink?> = dao.observeSyncState().map { it?.toLink() }

    suspend fun sheetsLinkNow(): SheetsLink? = dao.getSyncState()?.toLink()

    suspend fun saveSheetsLink(link: SheetsLink) = dao.putSyncState(
        SyncStateEntity(
            accountEmail = link.accountEmail,
            spreadsheetId = link.spreadsheetId,
            lastSyncedAt = link.lastSyncedAt?.toEpochMilli(),
            needsReconnect = link.needsReconnect
        )
    )

    suspend fun clearSheetsLink() = dao.clearSyncState()

    // ---- months waiting for the sheet ----

    fun observeDirtyCount(): Flow<Int> = dao.observeDirtyCount()

    suspend fun dirtyMonths(): List<YearMonth> = dao.dirtyMonths().map(SheetMonths::parse)

    suspend fun markDirty(month: YearMonth) = dao.markDirty(listOf(DirtyMonthEntity(SheetMonths.key(month))))

    suspend fun clearDirty(month: YearMonth) = dao.clearDirty(SheetMonths.key(month))

    /** Every month that holds an entry - for a fresh connection, a rename or a reset. */
    suspend fun markEveryMonthDirty() {
        val months = dao.entryStarts().map { SheetMonths.of(it.startMillis, it.zoneId) }.distinct()
        dao.markDirty(months.map { DirtyMonthEntity(SheetMonths.key(it)) })
    }

    /** The entries that belong on one month's tab, oldest first. */
    suspend fun entriesForMonth(month: YearMonth): List<Entry> {
        val (from, to) = SheetMonths.searchWindow(month)
        return dao.entriesStartingBetween(from, to)
            .filter { SheetMonths.of(it.startMillis, it.zoneId) == month }
            .map { it.toEntry() }
    }

    private suspend fun markDirty(startMillis: Long, zoneId: String) =
        dao.markDirty(listOf(DirtyMonthEntity(SheetMonths.key(SheetMonths.of(startMillis, zoneId)))))

    // ---- reading entries ----

    suspend fun latestEntry(): Entry? = dao.latestEntry()?.toEntry()

    fun observeLatestEntry(): Flow<Entry?> = dao.observeLatestEntry().map { it?.toEntry() }

    suspend fun entry(id: Long): Entry? = dao.getEntry(id)?.toEntry()

    fun observeBetween(from: Instant, to: Instant): Flow<List<Entry>> =
        dao.observeOverlapping(from.toEpochMilli(), to.toEpochMilli())
            .map { rows -> rows.map { it.toEntry() } }

    suspend fun entriesBetween(from: Instant, to: Instant): List<Entry> =
        dao.getOverlapping(from.toEpochMilli(), to.toEpochMilli()).map { it.toEntry() }

    /** Entries touching a local calendar day, including ones that cross midnight. */
    fun observeDay(date: LocalDate, zone: ZoneId): Flow<List<Entry>> {
        val (from, to) = dayBounds(date, zone)
        return observeBetween(from, to)
    }

    // ---- writing entries ----

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data class Invalid(val reason: String) : SaveResult
    }

    suspend fun log(
        start: Instant,
        end: Instant,
        text: String?,
        element: Element?,
        zone: ZoneId = ZoneId.systemDefault()
    ): SaveResult = save(existingId = null, start, end, text, element, zone)

    suspend fun update(
        id: Long,
        start: Instant,
        end: Instant,
        text: String?,
        element: Element?
    ): SaveResult = save(existingId = id, start, end, text, element, ZoneId.systemDefault())

    suspend fun delete(id: Long) = db.withTransaction {
        val existing = dao.getEntry(id) ?: return@withTransaction
        dao.softDelete(id, System.currentTimeMillis())
        markDirty(existing.startMillis, existing.zoneId)
    }

    /**
     * Entries may overlap freely. Doing two things at once - drinking water during
     * an hour of work - is two entries sharing time, and refusing the second one
     * gets in the way of the one thing Sumi is for. Totals count shared time once
     * instead; see [Intervals]. Only entries that make no sense are rejected.
     */
    private suspend fun save(
        existingId: Long?,
        start: Instant,
        end: Instant,
        text: String?,
        element: Element?,
        zone: ZoneId
    ): SaveResult {
        val cleanText = text?.trim()?.ifBlank { null }
        if (cleanText == null && element == null) {
            return SaveResult.Invalid("Type what you're doing, or choose an element.")
        }
        if (!end.isAfter(start)) return SaveResult.Invalid("The end has to come after the start.")
        if (Duration.between(start, end) > MAX_ENTRY) {
            return SaveResult.Invalid("An entry can't be longer than a day.")
        }

        return db.withTransaction {
            val now = System.currentTimeMillis()
            if (existingId == null) {
                val id = dao.insertEntry(
                    EntryEntity(
                        startMillis = start.toEpochMilli(),
                        endMillis = end.toEpochMilli(),
                        zoneId = zone.id,
                        text = cleanText,
                        element = element?.name,
                        updatedAt = now,
                        syncedAt = null
                    )
                )
                markDirty(start.toEpochMilli(), zone.id)
                SaveResult.Saved(id)
            } else {
                val existing = dao.getEntry(existingId)
                    ?: return@withTransaction SaveResult.Invalid("That entry no longer exists.")
                dao.updateEntry(
                    existing.copy(
                        startMillis = start.toEpochMilli(),
                        endMillis = end.toEpochMilli(),
                        text = cleanText,
                        element = element?.name,
                        updatedAt = now
                    )
                )
                // Both months: the one it was on, and the one it may have moved to.
                markDirty(existing.startMillis, existing.zoneId)
                markDirty(start.toEpochMilli(), existing.zoneId)
                SaveResult.Saved(existingId)
            }
        }
    }

    // ---- resetting ----

    /**
     * Every entry gone; goals and settings untouched. The months are queued first,
     * while the entries still say which months they were in, so a connected sheet
     * has those tabs emptied too.
     */
    suspend fun clearLog() = db.withTransaction {
        markEveryMonthDirty()
        dao.purgeEntries()
    }

    /** Names cleared, elements and rhythm back to defaults; the log untouched. */
    suspend fun resetGoalsAndSettings() = db.withTransaction {
        dao.resetGoals(Element.defaultOrder.map { it.name })
        saveSettings(Settings.Default)
        markEveryMonthDirty()
    }

    /**
     * Sumi as it was when installed. One transaction, so it never half-happens.
     * Google Sheets is disconnected, but the spreadsheet is left alone: Sumi never
     * deletes anything from someone's Drive.
     */
    suspend fun eraseEverything() = db.withTransaction {
        dao.purgeEntries()
        dao.resetGoals(Element.defaultOrder.map { it.name })
        saveSettings(Settings.Default)
        dao.clearSyncState()
        dao.clearAllDirty()
    }

    // ---- mapping ----

    private fun GoalEntity.toGoal() = Goal(
        slot = slot,
        name = name,
        element = Element.fromStored(element) ?: Element.forSlot(slot)
    )

    private fun EntryEntity.toEntry() = Entry(
        id = id,
        start = Instant.ofEpochMilli(startMillis),
        end = Instant.ofEpochMilli(endMillis),
        zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault()),
        text = text,
        element = Element.fromStored(element)
    )

    private fun SettingsEntity.toSettings() = Settings(
        askInterval = Duration.ofMinutes(askIntervalMinutes.toLong()),
        quietStart = LocalTime.ofSecondOfDay(quietStartMinute * 60L),
        quietEnd = LocalTime.ofSecondOfDay(quietEndMinute * 60L)
    )

    private fun SyncStateEntity.toLink() = SheetsLink(
        accountEmail = accountEmail,
        spreadsheetId = spreadsheetId,
        lastSyncedAt = lastSyncedAt?.let(Instant::ofEpochMilli),
        needsReconnect = needsReconnect
    )

    companion object {
        private val MAX_ENTRY: Duration = Duration.ofHours(24)

        fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
            date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

        @Volatile
        private var instance: SumiRepository? = null

        fun get(context: Context): SumiRepository =
            instance ?: synchronized(this) {
                instance ?: SumiRepository(SumiDatabase.get(context)).also { instance = it }
            }
    }
}
