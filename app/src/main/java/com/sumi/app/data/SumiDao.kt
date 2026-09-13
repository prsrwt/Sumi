package com.sumi.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    // ---- day entries ----

    @Query("SELECT * FROM day_entries WHERE epochDay = :epochDay")
    abstract fun observeDay(epochDay: Long): Flow<DayEntryEntity?>

    @Query("SELECT * FROM day_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    abstract fun observeRange(from: Long, to: Long): Flow<List<DayEntryEntity>>

    @Query("SELECT * FROM day_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    abstract suspend fun getRange(from: Long, to: Long): List<DayEntryEntity>

    @Query("SELECT doneMask FROM day_entries WHERE epochDay = :epochDay")
    abstract suspend fun getMask(epochDay: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDay(entry: DayEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDays(entries: List<DayEntryEntity>)

    /**
     * Flips one goal for one day. Wrapped in a transaction so that rapid taps on
     * the widget cannot interleave a read and a write and lose a tick.
     */
    @Transaction
    open suspend fun toggleGoal(epochDay: Long, slot: Int) {
        require(slot in 0 until GOAL_COUNT) { "slot out of range: $slot" }
        val current = getMask(epochDay) ?: 0
        upsertDay(DayEntryEntity(epochDay, current xor (1 shl slot)))
    }
}
