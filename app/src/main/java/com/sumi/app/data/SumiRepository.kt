package com.sumi.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    suspend fun setGoalName(slot: Int, name: String) {
        require(slot in 0 until GOAL_COUNT) { "slot out of range: $slot" }
        dao.setGoalName(slot, name.trim())
    }

    suspend fun assignElement(slot: Int, element: Element) {
        require(slot in 0 until GOAL_COUNT) { "slot out of range: $slot" }
        dao.assignElement(slot, element.name)
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

    suspend fun delete(id: Long) = dao.softDelete(id, System.currentTimeMillis())

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
                SaveResult.Saved(existingId)
            }
        }
    }

    // ---- resetting ----

    /** Every entry gone; goals and settings untouched. */
    suspend fun clearLog() = dao.purgeEntries()

    /** Names cleared, elements and rhythm back to defaults; the log untouched. */
    suspend fun resetGoalsAndSettings() = db.withTransaction {
        dao.resetGoals(Element.defaultOrder.map { it.name })
        saveSettings(Settings.Default)
    }

    /** Sumi as it was when installed. One transaction, so it never half-happens. */
    suspend fun eraseEverything() = db.withTransaction {
        dao.purgeEntries()
        dao.resetGoals(Element.defaultOrder.map { it.name })
        saveSettings(Settings.Default)
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
