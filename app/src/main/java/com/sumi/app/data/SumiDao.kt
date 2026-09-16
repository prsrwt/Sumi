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
