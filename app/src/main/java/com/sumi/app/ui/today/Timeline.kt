package com.sumi.app.ui.today

import com.sumi.app.data.Entry
import java.time.Duration
import java.time.Instant

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

    fun loggedTotal(rows: List<TimelineRow>): Duration =
        rows.filterIsInstance<TimelineRow.Logged>()
            .fold(Duration.ZERO) { acc, row -> acc + Duration.between(row.shownStart, row.shownEnd) }
}
