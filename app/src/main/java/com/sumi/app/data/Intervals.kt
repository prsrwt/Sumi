package com.sumi.app.data

import java.time.Duration
import java.time.Instant

/**
 * Time covered by a set of possibly overlapping stretches.
 *
 * Entries may overlap - water drunk during an hour of work is two entries sharing
 * time - so adding their lengths would overcount. A day with an hour of work and
 * five minutes of water inside it is an hour logged, not an hour and five.
 */
object Intervals {

    /** Total length covered, counting any stretch that several intervals share once. */
    fun covered(intervals: List<Pair<Instant, Instant>>): Duration {
        var total = Duration.ZERO
        var runStart: Instant? = null
        var runEnd: Instant? = null

        for ((start, end) in intervals.filter { it.second.isAfter(it.first) }.sortedBy { it.first }) {
            val currentEnd = runEnd
            if (currentEnd == null || start.isAfter(currentEnd)) {
                // A clean break: bank the run so far and start a new one.
                if (runStart != null && currentEnd != null) total += Duration.between(runStart, currentEnd)
                runStart = start
                runEnd = end
            } else if (end.isAfter(currentEnd)) {
                // Overlapping or touching: extend the run rather than adding to it.
                runEnd = end
            }
        }
        if (runStart != null && runEnd != null) total += Duration.between(runStart, runEnd)
        return total
    }
}
