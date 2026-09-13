package com.sumi.app.data

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The time range the composer offers before the user touches anything.
 *
 * Recall degrades quickly, so the most accurate log is one close to the moment
 * with its start already filled in - the user confirms a range rather than
 * reconstructing it. The usual answer is "from where your last entry ended".
 *
 * But not always. After a night's sleep the last entry ended ten hours ago, and
 * continuing from it would propose one enormous block. So continuation is capped,
 * and beyond the cap the suggestion falls back to one interval before now,
 * leaving the real gap visible as unlogged time to backfill if the user wants.
 */
object RangeSuggestion {

    val MAX_CONTINUATION: Duration = Duration.ofHours(3)

    fun suggest(latestEnd: Instant?, now: Instant, interval: Duration): ClosedRange<Instant> {
        val end = now.truncatedTo(ChronoUnit.MINUTES)
        val continuesLast = latestEnd != null &&
            latestEnd.isBefore(end) &&
            Duration.between(latestEnd, end) <= MAX_CONTINUATION

        val start = if (continuesLast) latestEnd!! else end.minus(interval)
        return start..end
    }
}
