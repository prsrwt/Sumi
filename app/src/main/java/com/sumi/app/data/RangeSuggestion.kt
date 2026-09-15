package com.sumi.app.data

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The time range the composer offers before the user touches anything.
 *
 * Recall degrades quickly, so the most accurate log is one close to the moment
 * with its start already filled in - the user confirms a range rather than
 * reconstructing it. Three cases:
 *
 * - The last entry ended a little while ago: continue from where it ended.
 * - It ended just now, in this same minute: offer that entry's own stretch. The
 *   usual reason for logging twice in a row is "and I also did this during that
 *   time". Continuing from its end would give a zero-length range, and falling
 *   back to one interval ago - what this used to do - offered an arbitrary window
 *   unrelated to the entry just logged.
 * - It ended long ago, or there is none: one interval before now. After a night's
 *   sleep the last entry ended hours ago, and continuing from it would propose one
 *   enormous block, so continuation is capped.
 */
object RangeSuggestion {

    val MAX_CONTINUATION: Duration = Duration.ofHours(3)

    fun suggest(
        latestStart: Instant?,
        latestEnd: Instant?,
        now: Instant,
        interval: Duration
    ): ClosedRange<Instant> {
        val end = now.truncatedTo(ChronoUnit.MINUTES)

        if (latestStart != null && latestEnd != null && !latestEnd.isBefore(end)) {
            return latestStart..latestEnd
        }
        if (latestEnd != null && Duration.between(latestEnd, end) <= MAX_CONTINUATION) {
            return latestEnd..end
        }
        return end.minus(interval)..end
    }
}
