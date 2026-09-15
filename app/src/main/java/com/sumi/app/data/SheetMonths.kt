package com.sumi.app.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A row read for working out months: just where an entry starts. */
data class EntryStart(val startMillis: Long, val zoneId: String)

/**
 * Which month tab of the sheet an entry belongs on.
 *
 * An entry lives on the tab of the month it started in, in the time zone it was
 * logged in - so 23:30 on 30 September to 00:15 on 1 October is a September row,
 * and an entry logged while travelling stays where its own clock put it.
 */
object SheetMonths {

    fun of(startMillis: Long, zoneId: String): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(startMillis).atZone(zoneOf(zoneId)))

    fun key(month: YearMonth): String = month.toString() // "2026-09"

    fun parse(key: String): YearMonth = YearMonth.parse(key)

    /**
     * A generous millisecond window around a month, wide enough for any time
     * zone; the exact month is then checked per entry in its own zone.
     */
    fun searchWindow(month: YearMonth): Pair<Long, Long> {
        val from = month.atDay(1).minusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant()
        val to = month.plusMonths(1).atDay(1).plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant()
        return from.toEpochMilli() to to.toEpochMilli()
    }

    private fun zoneOf(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())
}
