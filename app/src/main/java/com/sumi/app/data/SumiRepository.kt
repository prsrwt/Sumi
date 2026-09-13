package com.sumi.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The single place that translates between stored rows and the domain types the
 * UI and the widget consume. Dates cross this boundary as [LocalDate]; epoch-day
 * longs never leak past it.
 */
class SumiRepository(private val dao: SumiDao) {

    // ---- goals ----

    fun observeGoals(): Flow<List<Goal>> =
        dao.observeGoals().map { rows -> rows.map { Goal(it.slot, it.name) } }

    suspend fun goalsNow(): List<Goal> =
        dao.getGoals().map { Goal(it.slot, it.name) }

    suspend fun setGoalName(slot: Int, name: String) {
        require(slot in 0 until GOAL_COUNT) { "slot out of range: $slot" }
        dao.setGoalName(slot, name.trim())
    }

    // ---- daily progress ----

    fun observeDay(date: LocalDate): Flow<DayProgress> =
        dao.observeDay(date.toEpochDay()).map { row ->
            DayProgress(date, row?.doneMask ?: 0)
        }

    suspend fun toggleGoal(date: LocalDate, slot: Int) =
        dao.toggleGoal(date.toEpochDay(), slot)

    /**
     * Every day from [from] to [to] inclusive, including days with no stored row.
     * The heatmap needs a cell for every day, not just the ones that were touched.
     */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayProgress>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay())
            .map { rows -> densify(from, to, rows) }

    suspend fun rangeNow(from: LocalDate, to: LocalDate): List<DayProgress> =
        densify(from, to, dao.getRange(from.toEpochDay(), to.toEpochDay()))

    /**
     * Overwrites the given days outright. Used by the debug sample-history action;
     * writing zero masks is how it resets, so no table-wide delete is needed.
     */
    suspend fun overwriteDays(days: List<DayProgress>) =
        dao.upsertDays(days.map { DayEntryEntity(it.date.toEpochDay(), it.doneMask) })

    private fun densify(
        from: LocalDate,
        to: LocalDate,
        rows: List<DayEntryEntity>
    ): List<DayProgress> {
        val masks = rows.associate { it.epochDay to it.doneMask }
        val days = mutableListOf<DayProgress>()
        var day = from
        while (!day.isAfter(to)) {
            days += DayProgress(day, masks[day.toEpochDay()] ?: 0)
            day = day.plusDays(1)
        }
        return days
    }

    companion object {
        @Volatile
        private var instance: SumiRepository? = null

        fun get(context: Context): SumiRepository =
            instance ?: synchronized(this) {
                instance ?: SumiRepository(SumiDatabase.get(context).dao())
                    .also { instance = it }
            }
    }
}
