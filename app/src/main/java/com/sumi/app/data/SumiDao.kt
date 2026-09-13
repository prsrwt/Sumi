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

    @Query("UPDATE goals SET name = :name WHERE slot = :slot")
    abstract suspend fun setGoalName(slot: Int, name: String)

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

    /** Rows never synced, or changed (including deleted) since they last were. */
    @Query("SELECT * FROM entries WHERE syncedAt IS NULL OR updatedAt > syncedAt ORDER BY startMillis")
    abstract suspend fun unsyncedEntries(): List<EntryEntity>

    @Query("UPDATE entries SET syncedAt = :at WHERE id IN (:ids)")
    abstract suspend fun markSynced(ids: List<Long>, at: Long)

    // ---- settings ----

    @Query("SELECT * FROM settings WHERE id = 0")
    abstract fun observeSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    abstract suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSettings(settings: SettingsEntity)

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
