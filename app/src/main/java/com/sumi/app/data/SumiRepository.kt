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

    // ---- domains and activities ----

    fun observeDomains(): Flow<List<Domain>> =
        dao.observeDomains().map { rows -> rows.map { it.toDomain() } }

    suspend fun domainsNow(): List<Domain> = dao.getDomains().map { it.toDomain() }

    fun observeActivities(): Flow<List<Activity>> =
        dao.observeActivities().map { rows -> rows.map { it.toActivity() } }

    /**
     * Adds a domain under an element, or returns the one already there. Two
     * domains with the same name under one element would be indistinguishable on
     * every screen, so the name is what identifies it.
     */
    suspend fun addDomain(name: String, element: Element): Domain? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        return db.withTransaction {
            val existing = dao.domainNamed(element.name, clean)
            if (existing != null) return@withTransaction existing.toDomain()
            val position = dao.nextDomainPosition(element.name)
            val id = dao.insertDomain(DomainEntity(name = clean, element = element.name, position = position))
            if (id <= 0) null else Domain(id, clean, element, position)
        }
    }

    suspend fun renameDomain(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        db.withTransaction {
            dao.renameDomain(id, clean)
            markEveryMonthDirty()
        }
    }

    /**
     * Moves a domain to another element. Entries keep the element they were logged
     * under: the pentagon is a record of where time went, not of where it would go
     * if today's arrangement had always been true.
     */
    suspend fun moveDomain(id: Long, element: Element) = db.withTransaction {
        dao.setDomainElement(id, element.name, dao.nextDomainPosition(element.name))
        markEveryMonthDirty()
    }

    /** The domain goes, its activities go with it, and the entries keep their notes. */
    suspend fun removeDomain(id: Long) = db.withTransaction {
        dao.untagDomain(id)
        dao.deleteDomain(id)
        markEveryMonthDirty()
    }

    suspend fun addActivity(domainId: Long, name: String): Activity? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        return db.withTransaction {
            val existing = dao.activityNamed(domainId, clean)
            if (existing != null) return@withTransaction existing.toActivity()
            val id = dao.insertActivity(ActivityEntity(domainId = domainId, name = clean))
            if (id <= 0) null else Activity(id, domainId, clean, uses = 0, lastUsedAt = null)
        }
    }

    suspend fun renameActivity(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        db.withTransaction {
            dao.renameActivity(id, clean)
            markEveryMonthDirty()
        }
    }

    /**
     * An activity lives in one domain at a time, so moving it takes it out of the
     * one it was in. Entries logged with it follow, since the activity is the same
     * thing wherever it is kept.
     */
    suspend fun moveActivity(id: Long, toDomainId: Long) = db.withTransaction {
        dao.setActivityDomain(id, toDomainId)
        dao.retagEntriesOfActivity(id, toDomainId)
        markEveryMonthDirty()
    }

    suspend fun removeActivity(id: Long) = db.withTransaction {
        dao.untagActivity(id)
        dao.deleteActivity(id)
        markEveryMonthDirty()
    }

    suspend fun activitiesIn(domainId: Long): List<Activity> =
        dao.activitiesIn(domainId).map { it.toActivity() }

    /** What the composer offers under an element: your own words, most recent first. */
    suspend fun recentActivities(element: Element, limit: Int = RECENT_ACTIVITIES): List<Activity> =
        dao.recentActivities(element.name, limit).map { it.toActivity() }

    // ---- settings ----

    fun observeSettings(): Flow<Settings> =
        dao.observeSettings().map { it?.toSettings() ?: Settings.Default }

    suspend fun settingsNow(): Settings = dao.getSettings()?.toSettings() ?: Settings.Default

    suspend fun saveSettings(settings: Settings) = dao.saveRhythm(
        interval = settings.askInterval.toMinutes().toInt(),
        quietStart = settings.quietStart.toSecondOfDay() / 60,
        quietEnd = settings.quietEnd.toSecondOfDay() / 60
    )

    // ---- the first-launch introduction ----

    /** Null until the settings row has been read, so the app can wait instead of flashing a screen. */
    fun observeOnboarded(): Flow<Boolean?> = dao.observeOnboarded()

    suspend fun finishOnboarding() = dao.setOnboardedAt(System.currentTimeMillis())

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

    fun observeAllEntries(): Flow<List<Entry>> =
        dao.observeAllEntries().map { rows -> rows.map { it.toEntry() } }

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
        activityId: Long? = null,
        zone: ZoneId = ZoneId.systemDefault()
    ): SaveResult = save(existingId = null, start, end, text, element, activityId, zone)

    suspend fun update(
        id: Long,
        start: Instant,
        end: Instant,
        text: String?,
        element: Element?,
        activityId: Long? = null
    ): SaveResult = save(existingId = id, start, end, text, element, activityId, ZoneId.systemDefault())

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
        activityId: Long?,
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
            // The activity says which domain, so the two can never disagree. A word
            // that has been deleted since it was offered simply tags nothing.
            val activity = activityId?.let { dao.activity(it) }
            if (activity != null) dao.touchActivity(activity.id, now)
            if (existingId == null) {
                val id = dao.insertEntry(
                    EntryEntity(
                        startMillis = start.toEpochMilli(),
                        endMillis = end.toEpochMilli(),
                        zoneId = zone.id,
                        text = cleanText,
                        element = element?.name,
                        domainId = activity?.domainId,
                        activityId = activity?.id,
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
                        domainId = activity?.domainId ?: existing.domainId,
                        activityId = activity?.id ?: existing.activityId,
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
        // The domains and their activities are those names in another form, so
        // they go with them. Entries keep their notes and their elements.
        dao.purgeDomains()
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
        dao.purgeDomains()
        saveSettings(Settings.Default)
        // As installed means the introduction greets them again, too.
        dao.setOnboardedAt(null)
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
        element = Element.fromStored(element),
        domainId = domainId,
        activityId = activityId
    )

    private fun DomainEntity.toDomain() = Domain(
        id = id,
        name = name,
        element = Element.fromStored(element) ?: Element.EARTH,
        position = position
    )

    private fun ActivityEntity.toActivity() = Activity(
        id = id,
        domainId = domainId,
        name = name,
        uses = uses,
        lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli)
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

        /** How many words the composer offers under one element. */
        const val RECENT_ACTIVITIES = 8

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
