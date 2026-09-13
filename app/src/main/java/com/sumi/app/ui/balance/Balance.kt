package com.sumi.app.ui.balance

import com.sumi.app.data.Element
import com.sumi.app.data.Entry
import com.sumi.app.data.SumiRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day in the 30-day grid: how many of the five elements got any time. */
data class DayMark(val date: LocalDate, val elementsTouched: Int)

data class BalanceSnapshot(
    val windowDays: Int,
    val perElement: Map<Element, Duration>,
    val untagged: Duration,
    /** The single element to mention gently, if one has gone quiet. */
    val quietElement: Element?,
    val days: List<DayMark>
) {
    val tagged: Duration get() = perElement.values.fold(Duration.ZERO, Duration::plus)
    val isEmpty: Boolean get() = tagged.isZero && untagged.isZero
}

/**
 * Where the time went, as numbers the balance screen can draw.
 *
 * The window rolls: the last N days up to now, never a calendar week. A week that
 * resets on Sunday night manufactures a cliff - one bad Saturday and the whole
 * picture reads as failure until Monday. Rolling, the shape just drifts.
 *
 * Pure, so the rules can be checked without a device.
 */
object Balance {

    /** How long an element can go untouched before it is mentioned. */
    val QUIET_AFTER: Duration = Duration.ofDays(3)

    fun compute(
        entries: List<Entry>,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
        windowDays: Int,
        gridDays: Int
    ): BalanceSnapshot {
        val windowStart = today.minusDays((windowDays - 1).toLong()).atStartOfDay(zone).toInstant()

        val perElement = Element.entries.associateWith { Duration.ZERO }.toMutableMap()
        var untagged = Duration.ZERO
        for (entry in entries) {
            val slice = entry.overlapWith(windowStart, now)
            if (slice.isZero) continue
            val element = entry.element
            if (element == null) untagged += slice else perElement[element] = perElement.getValue(element) + slice
        }

        return BalanceSnapshot(
            windowDays = windowDays,
            perElement = perElement,
            untagged = untagged,
            quietElement = quietElement(entries, perElement, now),
            days = grid(entries, today, zone, gridDays)
        )
    }

    /**
     * An element is only called quiet if it has had nothing for a few days while
     * something else has had time in that same stretch. Otherwise the user is
     * simply away, and saying so about one element would be noise.
     *
     * Nor in the first days of use: with no history older than the quiet stretch,
     * every element is vacuously "untouched for days", and naming one would be
     * commenting on a log that has not had time to exist.
     *
     * When several are quiet, the one with the least time in the window is named,
     * so the line points at the element furthest out of the picture. One line at
     * most - a list of neglected things would be a scorecard by another name.
     */
    private fun quietElement(entries: List<Entry>, perElement: Map<Element, Duration>, now: Instant): Element? {
        val recentFrom = now.minus(QUIET_AFTER)
        if (entries.none { it.start.isBefore(recentFrom) }) return null

        val recent = entries.filter { !it.overlapWith(recentFrom, now).isZero }
        val touchedRecently = recent.mapNotNull { it.element }.toSet()
        if (touchedRecently.isEmpty()) return null

        return Element.entries
            .filter { it !in touchedRecently }
            .minByOrNull { perElement.getValue(it) }
    }

    private fun grid(entries: List<Entry>, today: LocalDate, zone: ZoneId, gridDays: Int): List<DayMark> =
        (gridDays - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val (from, to) = SumiRepository.dayBounds(date, zone)
            val touched = entries
                .filter { it.element != null && !it.overlapWith(from, to).isZero }
                .mapNotNull { it.element }
                .toSet()
            DayMark(date, touched.size)
        }
}
