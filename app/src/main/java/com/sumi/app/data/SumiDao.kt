package com.sumi.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SumiDao {

    // ---- goals ----

    @Query("SELECT * FROM goals ORDER BY slot")
    abstract fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY slot")
    abstract suspend fun getGoals(): List<GoalEntity>

    @Query("UPDATE goals SET name = :name WHERE element = :element AND name != :name")
    abstract suspend fun setGoalNameForElement(element: String, name: String): Int

    /** Returns how many rows changed: 0 when the name was already this. */
    @Query("UPDATE goals SET name = :name WHERE slot = :slot AND name != :name")
    abstract suspend fun setGoalName(slot: Int, name: String): Int

    @Query("UPDATE goals SET element = :element WHERE slot = :slot")
    protected abstract suspend fun setGoalElement(slot: Int, element: String)

    @Query("SELECT slot FROM goals WHERE element = :element")
    protected abstract suspend fun slotHolding(element: String): Int?

    /**
     * Gives [slot] the [element], swapping with whichever goal held it.
     *
     * The unique index on element means a straight swap would collide halfway
     * through, since SQLite checks the constraint per statement. So the other
     * goal is parked on a placeholder first. The transaction means no observer
     * ever sees the placeholder.
     */
    @Transaction
    open suspend fun assignElement(slot: Int, element: String) {
        val current = getGoals().firstOrNull { it.slot == slot } ?: return
        if (current.element == element) return

        val holder = slotHolding(element)
        if (holder == null) {
            setGoalElement(slot, element)
            return
        }
        setGoalElement(holder, SWAP_PLACEHOLDER)
        setGoalElement(slot, element)
        setGoalElement(holder, current.element)
    }

    // ---- domains and activities ----

    @Query("SELECT * FROM domains ORDER BY element, position, name")
    abstract fun observeDomains(): Flow<List<DomainEntity>>

    @Query("SELECT * FROM domains ORDER BY element, position, name")
    abstract suspend fun getDomains(): List<DomainEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertDomain(domain: DomainEntity): Long

    @Query("SELECT * FROM domains WHERE element = :element AND name = :name LIMIT 1")
    abstract suspend fun domainNamed(element: String, name: String): DomainEntity?

    @Query("SELECT IFNULL(MAX(position), -1) + 1 FROM domains WHERE element = :element")
    abstract suspend fun nextDomainPosition(element: String): Int

    @Query("UPDATE domains SET name = :name WHERE id = :id")
    abstract suspend fun renameDomain(id: Long, name: String)

    @Query("UPDATE domains SET position = :position WHERE id = :id")
    abstract suspend fun setDomainPosition(id: Long, position: Int)

    @Query("SELECT COUNT(*) FROM activities WHERE domainId = :domainId")
    abstract suspend fun activityCount(domainId: Long): Int

    @Query("UPDATE domains SET element = :element, position = :position WHERE id = :id")
    abstract suspend fun setDomainElement(id: Long, element: String, position: Int)

    @Query("DELETE FROM domains WHERE id = :id")
    abstract suspend fun deleteDomain(id: Long)

    @Query("SELECT * FROM activities ORDER BY name")
    abstract fun observeActivities(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities")
    abstract suspend fun allActivities(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE domainId = :domainId ORDER BY name")
    abstract suspend fun activitiesIn(domainId: Long): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertActivity(activity: ActivityEntity): Long

    @Query("SELECT * FROM activities WHERE domainId = :domainId AND name = :name LIMIT 1")
    abstract suspend fun activityNamed(domainId: Long, name: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE id = :id")
    abstract suspend fun activity(id: Long): ActivityEntity?

    @Query("UPDATE activities SET name = :name WHERE id = :id")
    abstract suspend fun renameActivity(id: Long, name: String)

    @Query("UPDATE activities SET domainId = :domainId WHERE id = :id")
    abstract suspend fun setActivityDomain(id: Long, domainId: Long)

    @Query("UPDATE activities SET uses = uses + 1, lastUsedAt = :at WHERE id = :id")
    abstract suspend fun touchActivity(id: Long, at: Long)

    @Query("DELETE FROM activities WHERE id = :id")
    abstract suspend fun deleteActivity(id: Long)

    /**
     * The words used under one element, the most recently used first. What the
     * composer offers, so the list is always the user's own habits rather than
     * anything Sumi decided.
     */
    @Query(
        "SELECT a.* FROM activities a JOIN domains d ON a.domainId = d.id " +
            "WHERE d.element = :element ORDER BY a.lastUsedAt DESC, a.uses DESC, a.name LIMIT :limit"
    )
    abstract suspend fun recentActivities(element: String, limit: Int): List<ActivityEntity>

    /**
     * The words used anywhere, most recently used first, each with the element it
     * belongs to. What the composer offers before an element has been chosen.
     */
    @Query(
        "SELECT a.id AS id, a.name AS name, a.domainId AS domainId, d.element AS element, " +
            "a.uses AS uses, a.lastUsedAt AS lastUsedAt " +
            "FROM activities a JOIN domains d ON a.domainId = d.id " +
            "ORDER BY a.lastUsedAt IS NULL, a.lastUsedAt DESC, a.uses DESC, a.id LIMIT :limit"
    )
    abstract suspend fun recentActivitiesEverywhere(limit: Int): List<ActivityWithElement>

    /** The word by that name under an element, whichever domain holds it. */
    @Query(
        "SELECT a.id AS id, a.name AS name, a.domainId AS domainId, d.element AS element, " +
            "a.uses AS uses, a.lastUsedAt AS lastUsedAt " +
            "FROM activities a JOIN domains d ON a.domainId = d.id " +
            "WHERE d.element = :element AND a.name = :name COLLATE NOCASE LIMIT 1"
    )
    abstract suspend fun activityUnder(element: String, name: String): ActivityWithElement?

    /** Where a new word goes by default: the first domain the element was given. */
    @Query("SELECT * FROM domains WHERE element = :element ORDER BY position, id LIMIT 1")
    abstract suspend fun firstDomain(element: String): DomainEntity?

    /** Entries keep their note when a tag disappears, rather than pointing at nothing. */
    @Query("UPDATE entries SET activityId = NULL WHERE activityId = :id")
    abstract suspend fun untagActivity(id: Long)

    @Query("UPDATE entries SET domainId = :domainId, activityId = :activityId, element = :element WHERE id = :id")
    abstract suspend fun tagEntry(id: Long, domainId: Long, activityId: Long, element: String)

    @Query("UPDATE entries SET domainId = NULL, activityId = NULL WHERE domainId = :id")
    abstract suspend fun untagDomain(id: Long)

    @Query("UPDATE entries SET domainId = :domainId WHERE activityId = :activityId")
    abstract suspend fun retagEntriesOfActivity(activityId: Long, domainId: Long)

    // ---- entries ----

    @Insert
    abstract suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    abstract suspend fun updateEntry(entry: EntryEntity)

    @Query("SELECT * FROM entries WHERE id = :id AND deletedAt IS NULL")
    abstract suspend fun getEntry(id: Long): EntryEntity?

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY endMillis DESC LIMIT 1")
    abstract suspend fun latestEntry(): EntryEntity?

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY endMillis DESC LIMIT 1")
    abstract fun observeLatestEntry(): Flow<EntryEntity?>

    /** Every entry that overlaps the half-open window [from, to). */
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND startMillis < :to AND endMillis > :from ORDER BY startMillis")
    abstract fun observeOverlapping(from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND startMillis < :to AND endMillis > :from ORDER BY startMillis")
    abstract suspend fun getOverlapping(from: Long, to: Long): List<EntryEntity>

    @Query("UPDATE entries SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    abstract suspend fun softDelete(id: Long, at: Long)

    // ---- the Google Sheets link ----

    @Query("SELECT * FROM sync_state WHERE id = 0")
    abstract fun observeSyncState(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 0")
    abstract suspend fun getSyncState(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    abstract suspend fun clearSyncState()

    // ---- months waiting for the sheet ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun markDirty(months: List<DirtyMonthEntity>)

    @Query("SELECT month FROM dirty_months ORDER BY month")
    abstract suspend fun dirtyMonths(): List<String>

    @Query("SELECT COUNT(*) FROM dirty_months")
    abstract fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM dirty_months WHERE month = :month")
    abstract suspend fun clearDirty(month: String)

    @Query("DELETE FROM dirty_months")
    abstract suspend fun clearAllDirty()

    /** Where every live entry starts, to work out which months it lives in. */
    @Query("SELECT startMillis, zoneId FROM entries WHERE deletedAt IS NULL")
    abstract suspend fun entryStarts(): List<EntryStart>

    /** Everything ever logged, for the history sheet's month calendars. */
    @Query("SELECT * FROM entries WHERE deletedAt IS NULL ORDER BY startMillis")
    abstract fun observeAllEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deletedAt IS NULL AND startMillis >= :from AND startMillis < :to ORDER BY startMillis, id")
    abstract suspend fun entriesStartingBetween(from: Long, to: Long): List<EntryEntity>

    // ---- settings ----

    @Query("SELECT * FROM settings WHERE id = 0")
    abstract fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    abstract suspend fun getSettings(): SettingsEntity?

    /**
     * Only the rhythm columns. Replacing the whole row would also wipe fields that
     * belong to other features, such as when the introduction was finished.
     */
    @Query("UPDATE settings SET askIntervalMinutes = :interval, quietStartMinute = :quietStart, quietEndMinute = :quietEnd WHERE id = 0")
    abstract suspend fun saveRhythm(interval: Int, quietStart: Int, quietEnd: Int)

    @Query("SELECT onboardedAt IS NOT NULL FROM settings WHERE id = 0")
    abstract fun observeOnboarded(): Flow<Boolean?>

    @Query("UPDATE settings SET onboardedAt = :at WHERE id = 0")
    abstract suspend fun setOnboardedAt(at: Long?)

    // ---- resetting ----

    /**
     * Removes every entry outright, soft-deleted rows included. A reset is the
     * user asking for the data to be gone, so nothing is kept behind a flag.
     */
    @Query("DELETE FROM entries")
    abstract suspend fun purgeEntries()

    @Query("UPDATE goals SET name = ''")
    protected abstract suspend fun clearGoalNames()

    @Query("DELETE FROM domains")
    abstract suspend fun purgeDomains()

    /**
     * Clears every name and puts each goal back on its original element.
     *
     * Every goal is parked on its own placeholder first: moving them straight to
     * their defaults would, part-way through, have two goals holding the same
     * element, which the unique index rejects.
     */
    @Transaction
    open suspend fun resetGoals(defaultElements: List<String>) {
        clearGoalNames()
        defaultElements.indices.forEach { slot -> setGoalElement(slot, "$RESET_PLACEHOLDER$slot") }
        defaultElements.forEachIndexed { slot, element -> setGoalElement(slot, element) }
    }

    private companion object {
        const val SWAP_PLACEHOLDER = "__swap__"
        const val RESET_PLACEHOLDER = "__reset_"
    }
}
