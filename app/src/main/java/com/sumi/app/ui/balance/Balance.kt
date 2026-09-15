package com.sumi.app.ui.balance

import com.sumi.app.data.Element
import com.sumi.app.data.Entry
import com.sumi.app.data.Intervals
import com.sumi.app.data.SumiRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day in the 30-day grid: how many of the five elements got any time. */
data class DayMark(val date: LocalDate, val elementsTouched: Int)

/** An element that has taken most of the time: over half of it, or long weeks. */
data class Heavy(val element: Element, val longWeeks: Boolean)

data class BalanceSnapshot(
    val windowDays: Int,
    val perElement: Map<Element, Duration>,
    val untagged: Duration,
    /** The single element to mention gently, if one has gone quiet. */
    val quietElement: Element?,
    /** The element to mention gently, if one has taken most of the time. */
    val heavy: Heavy?,
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

    /**
     * One goal holding more than this share of tagged time is mentioned. Balance
     * research measures life balance as time shared across the domains that
     * matter (Sheldon, Cummins and Kamble, 2010); more than half in one of five is
     * the point where that one outweighs all the others together.
     */
    const val MAJORITY_SHARE = 0.5

    /**
     * The WHO and ILO (2021) found working 55 or more hours a week raises the risk
     * of stroke by about 35% and of dying from heart disease by about 17%. A goal
     * averaging more than this is mentioned whatever its share.
     */
    val LONG_WEEK: Duration = Duration.ofHours(55)

    /** Too little logged for shares to mean anything below this. */
    val MIN_TAGGED_FOR_SHARE: Duration = Duration.ofHours(10)

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

        // Each entry clipped to the window. Time is then totalled per element with
        // overlaps merged, so logging the same element twice for one stretch counts
        // it once - while different elements sharing a stretch each get all of it,
        // because both really happened.
        val slices = entries.mapNotNull { entry ->
            val start = maxOf(entry.start, windowStart)
            val end = minOf(entry.end, now)
            if (end.isAfter(start)) Triple(entry.element, start, end) else null
        }
        val perElement = Element.entries.associateWith { element ->
            Intervals.covered(slices.filter { it.first == element }.map { it.second to it.third })
        }
        val untagged = Intervals.covered(slices.filter { it.first == null }.map { it.second to it.third })

        return BalanceSnapshot(
            windowDays = windowDays,
            perElement = perElement,
            untagged = untagged,
            quietElement = quietElement(entries, perElement, now),
            heavy = heavy(entries, perElement, windowDays, now),
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
    /**
     * The calm opposite of a quiet element. Long weeks take precedence, since they
     * matter even when other goals also get time. Nothing is said in the first days
     * of use, for the same reason nothing is called quiet then.
     */
    private fun heavy(entries: List<Entry>, perElement: Map<Element, Duration>, windowDays: Int, now: Instant): Heavy? {
        if (entries.none { it.start.isBefore(now.minus(QUIET_AFTER)) }) return null
        val (element, time) = perElement.maxByOrNull { it.value } ?: return null
        if (time.isZero) return null

        val minutesPerWeek = time.toMinutes() * 7.0 / windowDays
        if (minutesPerWeek > LONG_WEEK.toMinutes()) return Heavy(element, longWeeks = true)

        val tagged = perElement.values.fold(Duration.ZERO, Duration::plus)
        if (tagged >= MIN_TAGGED_FOR_SHARE && time.toMinutes() > tagged.toMinutes() * MAJORITY_SHARE) {
            return Heavy(element, longWeeks = false)
        }
        return null
    }

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
