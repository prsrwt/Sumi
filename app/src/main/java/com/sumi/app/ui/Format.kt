package com.sumi.app.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared formatting, so every screen writes times and durations the same way. */
object Format {

    fun timeFormatter(context: Context): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")

    fun time(context: Context, instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter(context).format(instant.atZone(zone))

    /** "45m", "1h 10m", "3h". Rounded to the minute; nothing under a minute is shown. */
    fun duration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    /** The same length for a screen reader, which would read "1h 10m" awkwardly: "1 hour 10 minutes". */
    fun spokenDuration(duration: Duration): String {
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        fun count(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
        return when {
            hours == 0L -> count(minutes, "minute")
            minutes == 0L -> count(hours, "hour")
            else -> "${count(hours, "hour")} ${count(minutes, "minute")}"
        }
    }
}
