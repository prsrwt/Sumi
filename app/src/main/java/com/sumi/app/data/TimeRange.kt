package com.sumi.app.data

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns two clock times picked by hand into a real stretch of time.
 *
 * A clock time has no date, so one has to be chosen. The end goes on whichever
 * day keeps it closest to where it was before the edit - so nudging an entry's
 * end from 00:15 back to 23:50 lands on the evening before, rather than jumping a
 * whole day forward. The start then goes on the end's day, or the day before when
 * it would otherwise fall at or after the end: that is how 23:30 to 00:15 reads.
 */
object TimeRange {

    fun resolve(from: LocalTime, to: LocalTime, previousEnd: Instant, zone: ZoneId): ClosedRange<Instant> {
        val anchor = previousEnd.atZone(zone).toLocalDate()
        val end = listOf(-1L, 0L, 1L)
            .map { anchor.plusDays(it).atTime(to).atZone(zone).toInstant() }
            .minBy { Duration.between(it, previousEnd).abs() }

        val endDay = end.atZone(zone).toLocalDate()
        var start = endDay.atTime(from).atZone(zone).toInstant()
        if (!start.isBefore(end)) start = endDay.minusDays(1).atTime(from).atZone(zone).toInstant()
        return start..end
    }

    /** Length of a picked range for the live preview, crossing midnight when it must. */
    fun length(from: LocalTime, to: LocalTime): Duration {
        val minutes = Duration.between(from, to).toMinutes()
        return Duration.ofMinutes(if (minutes <= 0) minutes + 24 * 60 else minutes)
    }
}
