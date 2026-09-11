package com.beaver.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Beaver tracks exactly five goals, every day. */
const val GOAL_COUNT = 5

/**
 * One of the five fixed goal slots. Rows 0..4 always exist; the database seeds
 * them on creation so the rest of the app can assume five goals are present.
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val slot: Int,
    val name: String
)

/**
 * One day of history. Which goals were ticked is stored as a five-bit mask
 * rather than five rows, so a heatmap range query is a single scan and each
 * day costs one row.
 */
@Entity(tableName = "day_entries")
data class DayEntryEntity(
    @PrimaryKey val epochDay: Long,
    val doneMask: Int
)

/** Domain view of a goal slot. */
data class Goal(
    val slot: Int,
    val name: String
) {
    /** What the widget and setup screen show when the user hasn't named it yet. */
    val displayName: String get() = name.ifBlank { "Goal ${slot + 1}" }
}

/** Domain view of a single day's progress. */
data class DayProgress(
    val date: LocalDate,
    val doneMask: Int
) {
    fun isDone(slot: Int): Boolean = (doneMask shr slot) and 1 == 1

    val completedCount: Int get() = Integer.bitCount(doneMask)

    companion object {
        fun empty(date: LocalDate) = DayProgress(date, 0)
    }
}
