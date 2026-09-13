package com.sumi.app.ui.today

import com.sumi.app.data.Entry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** One line of a day's timesheet. */
sealed interface TimelineRow {
    /** An entry, with its times clipped to the day being shown. */
    data class Logged(val entry: Entry, val shownStart: Instant, val shownEnd: Instant) : TimelineRow

    /** Time between entries that nobody logged. Tappable, to backfill. */
    data class Unlogged(val from: Instant, val to: Instant) : TimelineRow
}

/**
 * Turns a day's entries into rows with the gaps made explicit.
 *
 * Gaps are shown between entries and after the last one up to now, but not
 * before the first entry: an "unlogged, 7h" row covering the night would read as
 * a reproach for sleeping. Short gaps are ignored, since a few minutes between
 * one thing and the next is just the day, not something missing.
 *
 * Pure function over its inputs, so the rules can be reasoned about without a
 * device.
 */
object Timeline {

    val MIN_GAP: Duration = Duration.ofMinutes(5)

    fun build(
        entries: List<Entry>,
        dayStart: Instant,
        dayEnd: Instant,
        now: Instant
    ): List<TimelineRow> {
        val rows = mutableListOf<TimelineRow>()
        var cursor: Instant? = null

        for (entry in entries.sortedBy { it.start }) {
            val shownStart = maxOf(entry.start, dayStart)
            val shownEnd = minOf(entry.end, dayEnd)
            if (!shownEnd.isAfter(shownStart)) continue

            cursor?.let { if (Duration.between(it, shownStart) >= MIN_GAP) rows += TimelineRow.Unlogged(it, shownStart) }
            rows += TimelineRow.Logged(entry, shownStart, shownEnd)
            cursor = if (cursor == null) shownEnd else maxOf(cursor, shownEnd)
        }

        val dayContainsNow = now.isAfter(dayStart) && now.isBefore(dayEnd)
        cursor?.let {
            if (dayContainsNow && Duration.between(it, now) >= MIN_GAP) rows += TimelineRow.Unlogged(it, now)
        }
        return rows
    }

    /** Where an entry reaches past the day it is being shown on. */
    sealed interface Spill {
        data class StartedEarlier(val at: Instant) : Spill
        data class EndsLater(val at: Instant) : Spill
    }

    /**
     * A row clipped to its day shows the day's share of the entry, which is the
     * right number for that day's total - but on its own "12:00 AM" hides that
     * the entry began the night before. This says where it really started (or,
     * failing that, ended), so the day's view and the balance view stop looking
     * like they disagree.
     */
    fun spill(row: TimelineRow.Logged): Spill? = when {
        row.entry.start.isBefore(row.shownStart) -> Spill.StartedEarlier(row.entry.start)
        row.entry.end.isAfter(row.shownEnd) -> Spill.EndsLater(row.entry.end)
        else -> null
    }

    /** "today", "yesterday" or "tomorrow" relative to the real today; null beyond that. */
    fun relativeDay(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        today.plusDays(1) -> "tomorrow"
        else -> null
    }

    fun loggedTotal(rows: List<TimelineRow>): Duration =
        rows.filterIsInstance<TimelineRow.Logged>()
            .fold(Duration.ZERO) { acc, row -> acc + Duration.between(row.shownStart, row.shownEnd) }
}
