package com.sumi.app.ui.balance

import com.sumi.app.data.Entry
import com.sumi.app.data.Intervals
import com.sumi.app.data.SumiRepository
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One day in the history: how many of the five it touched, and how long was logged. */
data class HistoryDay(
    val date: LocalDate,
    val elementsTouched: Int,
    val logged: Duration
) {
    val isEmpty: Boolean get() = logged.isZero
}

/** One month as a calendar: every day of it, in order. */
data class HistoryMonth(val month: YearMonth, val days: List<HistoryDay>)

/**
 * The whole log as month calendars, newest first, for the history sheet.
 *
 * Whole months rather than a rolling window: this is for finding a particular day
 * again, and people look for days by date. Balance itself stays rolling.
 */
object History {

    fun build(entries: List<Entry>, today: LocalDate, zone: ZoneId): List<HistoryMonth> {
        val first = entries.minOfOrNull { it.start.atZone(zone).toLocalDate() } ?: today
        val firstMonth = YearMonth.from(minOf(first, today))
        val lastMonth = YearMonth.from(today)

        val months = mutableListOf<HistoryMonth>()
        var month = lastMonth
        while (!month.isBefore(firstMonth)) {
            months += HistoryMonth(month, (1..month.lengthOfMonth()).map { day -> dayOf(month.atDay(day), entries, zone) })
            month = month.minusMonths(1)
        }
        return months
    }

    private fun dayOf(date: LocalDate, entries: List<Entry>, zone: ZoneId): HistoryDay {
        val (from, to) = SumiRepository.dayBounds(date, zone)
        val onDay = entries.filter { it.start.isBefore(to) && it.end.isAfter(from) }
        val clipped = onDay.map { maxOf(it.start, from) to minOf(it.end, to) }
        return HistoryDay(
            date = date,
            elementsTouched = onDay.mapNotNull { it.element }.distinct().size,
            logged = Intervals.covered(clipped)
        )
    }
}
