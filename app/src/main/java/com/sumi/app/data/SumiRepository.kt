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
            if (id > 0) seedWords(id, clean)
            nameSpokeAfterFirstDomain(element.name)
            if (id <= 0) null else Domain(id, clean, element, position)
        }
    }

    suspend fun renameDomain(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        db.withTransaction {
            val domain = dao.getDomains().firstOrNull { it.id == id } ?: return@withTransaction
            dao.renameDomain(id, clean)
            nameSpokeAfterFirstDomain(domain.element)
            markEveryMonthDirty()
        }
    }

    /**
     * Puts a domain at the head of its element, which is also what the pentagon
     * calls that spoke. Everything else shuffles down, so the order stays a list
     * the user arranged rather than whatever order things were added in.
     */
    suspend fun makeFirstDomain(id: Long) = db.withTransaction {
        val all = dao.getDomains()
        val domain = all.firstOrNull { it.id == id } ?: return@withTransaction
        val rest = all.filter { it.element == domain.element && it.id != id }
            .sortedBy { it.position }
        dao.setDomainPosition(id, 0)
        rest.forEachIndexed { index, other -> dao.setDomainPosition(other.id, index + 1) }
        nameSpokeAfterFirstDomain(domain.element)
        markEveryMonthDirty()
    }

    /**
     * Lays out the five as a ready-made set describes them.
     *
     * A part of life holding words the user has used is never thrown away: it is
     * kept and moved to the end of its element. Only empty ones the new set has no
     * use for are removed, so choosing a different life never costs anybody the
     * words they had built up.
     */
    suspend fun applyPreset(preset: Preset) = db.withTransaction {
        val parts = preset.parts
        Element.entries.forEach { element ->
            // An element holds what the chosen life says it holds, and nothing
            // else: a list of every part of life Sumi knows about would put twenty
            // of them in front of somebody who chose five. Anything already in use
            // survives, and moves to the end rather than being taken away.
            val wanted = parts[element].orEmpty().map { it.trim() }.filter { it.isNotBlank() }

            dao.getDomains()
                .filter { it.element == element.name }
                .filter { domain -> wanted.none { it.equals(domain.name, ignoreCase = true) } }
                .filter { dao.timesUsed(it.id) == 0 }
                .forEach { unused ->
                    dao.untagDomain(unused.id)
                    dao.deleteDomain(unused.id)
                }

            wanted.forEachIndexed { index, name ->
                val existing = dao.domainNamed(element.name, name)
                if (existing == null) {
                    val id = dao.insertDomain(DomainEntity(name = name, element = element.name, position = index))
                    if (id > 0) seedWords(id, name)
                } else {
                    dao.setDomainPosition(existing.id, index)
                }
            }

            dao.getDomains()
                .filter { it.element == element.name && wanted.none { w -> w.equals(it.name, ignoreCase = true) } }
                .sortedBy { it.position }
                .forEachIndexed { index, kept -> dao.setDomainPosition(kept.id, wanted.size + index) }

            // "None of these" leaves the spokes unnamed, so the pentagon shows the
            // elements themselves and nothing pretends to be a choice somebody made.
            // The parts of life stay either way: there is always somewhere to log.
            if (preset.isBlank) {
                dao.setGoalNameForElement(element.name, "")
            } else {
                nameSpokeAfterFirstDomain(element.name)
            }
        }
        markEveryMonthDirty()
    }

    /**
     * Puts the word on an entry that is already saved, which is how the question
     * after logging works: the hour is recorded first, and where it belongs is
     * answered afterwards or not at all.
     */
    suspend fun tagEntry(entryId: Long, word: Word) = db.withTransaction {
        // The part of life says the element too, so an hour sent without one lands
        // on its spoke rather than staying untagged.
        dao.tagEntry(entryId, word.domainId, word.id, word.element.name)
        dao.touchActivity(word.id, System.currentTimeMillis())
        val entry = dao.getEntry(entryId) ?: return@withTransaction
        markDirty(entry.startMillis, entry.zoneId)
    }

    /**
     * A part of life from the list arrives with its own words, so the composer has
     * something to offer before anybody has typed anything. They count as unused
     * until they are used, so they sit behind your own words, never in front.
     */
    private suspend fun seedWords(domainId: Long, name: String) {
        Domains.wordsFor(name).forEach { word ->
            dao.insertActivity(ActivityEntity(domainId = domainId, name = word))
        }
    }

    /** How many words a domain holds, for the line under its name. */
    suspend fun wordCount(domainId: Long): Int = dao.activityCount(domainId)

    /**
     * The spoke's name is the first domain under it, so the pentagon says "Health"
     * rather than "Earth" and stays true when the domains change. An element with
     * no domains left goes back to showing its own name.
     */
    private suspend fun nameSpokeAfterFirstDomain(element: String) {
        val first = dao.firstDomain(element)
        dao.setGoalNameForElement(element, first?.name.orEmpty())
    }

    /**
     * Moves a domain to another element. Entries keep the element they were logged
     * under: the pentagon is a record of where time went, not of where it would go
     * if today's arrangement had always been true.
     */
    suspend fun moveDomain(id: Long, element: Element) = db.withTransaction {
        val was = dao.getDomains().firstOrNull { it.id == id }?.element
        dao.setDomainElement(id, element.name, dao.nextDomainPosition(element.name))
        if (was != null) nameSpokeAfterFirstDomain(was)
        nameSpokeAfterFirstDomain(element.name)
        markEveryMonthDirty()
    }

    /** The domain goes, its activities go with it, and the entries keep their notes. */
    suspend fun removeDomain(id: Long) = db.withTransaction {
        val domain = dao.getDomains().firstOrNull { it.id == id }
        dao.untagDomain(id)
        dao.deleteDomain(id)
        if (domain != null) nameSpokeAfterFirstDomain(domain.element)
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

    /** Every word by id, for writing the sheet. */
    suspend fun wordNames(): Map<Long, String> =
        dao.allActivities().associate { it.id to it.name }

    suspend fun activitiesIn(domainId: Long): List<Activity> =
        dao.activitiesIn(domainId).map { it.toActivity() }

    /** What the composer offers under an element: your own words, most recent first. */
    suspend fun recentActivities(element: Element, limit: Int = RECENT_ACTIVITIES): List<Activity> =
        dao.recentActivities(element.name, limit).map { it.toActivity() }

    /**
     * What the composer offers: the words you have used, most recent first, and
     * then the ones your parts of life came with.
     *
     * The fresh ones are taken a turn at a time from each element rather than in
     * the order they were stored, or the row would open with eight words from
     * whichever part of life happened to be created first.
     */
    suspend fun recentWords(limit: Int = RECENT_ACTIVITIES): List<Word> {
        val rows = dao.recentActivitiesEverywhere(limit * 8)
        val used = rows.filter { it.lastUsedAt != null }.mapNotNull { it.toWord() }
        if (used.size >= limit) return used.take(limit)

        val fresh = Element.entries.map { element ->
            rows.filter { it.lastUsedAt == null && it.element == element.name }.mapNotNull { it.toWord() }
        }
        val spread = mutableListOf<Word>()
        var round = 0
        while (spread.size + used.size < limit && fresh.any { it.size > round }) {
            fresh.forEach { forElement ->
                forElement.getOrNull(round)?.let { if (spread.size + used.size < limit) spread.add(it) }
            }
            round++
        }
        return used + spread
    }

    /** The word already known under this element, if the typed line is one of them. */
    suspend fun wordUnder(element: Element, name: String): Word? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        return dao.activityUnder(element.name, clean)?.toWord()
    }

    /**
     * Where a word Sumi already knows would go, when it is one of the catalogue's.
     * A part of life you already have wins over the one the catalogue suggests:
     * "cooking" belongs with your Home and care, wherever you have put it.
     */
    suspend fun homeFor(word: String): WordHome? {
        val idea = Domains.ideaForWord(word) ?: return null
        val mine = dao.getDomains().firstOrNull { it.name.equals(idea.name, ignoreCase = true) }
        return if (mine == null) {
            WordHome(idea.name, idea.element, yours = false)
        } else {
            WordHome(mine.name, Element.fromStored(mine.element) ?: idea.element, yours = true)
        }
    }

    /**
     * Keeps a word where it belongs, adding that part of life first where it is
     * missing. Adding it names the spoke too if the element had no name yet, which
     * is how 水 stops reading "Water" and starts reading "People".
     */
    suspend fun keepWordAtHome(home: WordHome, word: String): Word? {
        val domain = dao.domainNamed(home.element.name, home.name)?.toDomain()
            ?: addDomain(home.name, home.element)
            ?: return null
        return keepWordIn(domain, word)
    }

    /** Keeps a word in the part of life the user pointed at. */
    suspend fun keepWordIn(domain: Domain, name: String): Word? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        return db.withTransaction {
            val existing = dao.activityNamed(domain.id, clean)
            if (existing != null) return@withTransaction Word(existing.id, existing.name, domain.id, domain.element)
            val id = dao.insertActivity(ActivityEntity(domainId = domain.id, name = clean))
            if (id <= 0) null else Word(id, clean, domain.id, domain.element)
        }
    }

    /**
     * Keeps a word under an element, in the first domain that element was given.
     * Where an element has no domain yet, one is made from the name on the goal,
     * so a word never has nowhere to live.
     */
    suspend fun keepWord(element: Element, name: String, goalName: String): Word? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        return db.withTransaction {
            val existing = dao.activityUnder(element.name, clean)
            if (existing != null) return@withTransaction existing.toWord()
            val domain = dao.firstDomain(element.name)
                ?: run {
                    val fallback = goalName.trim().ifBlank { element.displayName }
                    val id = dao.insertDomain(
                        DomainEntity(name = fallback, element = element.name, position = 0)
                    )
                    if (id <= 0) null else dao.domainNamed(element.name, fallback)
                }
                ?: return@withTransaction null
            val id = dao.insertActivity(ActivityEntity(domainId = domain.id, name = clean))
            if (id <= 0) null else Word(id, clean, domain.id, element)
        }
    }

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

    private fun ActivityWithElement.toWord(): Word? {
        val known = Element.fromStored(element) ?: return null
        return Word(id = id, name = name, domainId = domainId, element = known)
    }

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
