package com.sumi.app.ui.balance

import com.sumi.app.data.Element
import com.sumi.app.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class HistoryTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-09-16")

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)

    private var nextId = 1L
    private fun entry(from: String, to: String, element: Element?) =
        Entry(nextId++, at(from), at(to), utc, if (element == null) "untagged" else null, element)

    private fun build(entries: List<Entry>) = History.build(entries, today, utc)

    private fun day(months: List<HistoryMonth>, date: String): HistoryDay {
        val wanted = LocalDate.parse(date)
        return months.first { it.month == YearMonth.from(wanted) }.days.first { it.date == wanted }
    }

    @Test
    fun `months run from the first entry to this month, newest first`() {
        val months = build(listOf(entry("2026-07-20T09:00", "2026-07-20T10:00", Element.FIRE)))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8), YearMonth.of(2026, 7)), months.map { it.month })
    }

    @Test
    fun `with nothing logged there is still this month to look at`() {
        val months = build(emptyList())
        assertEquals(listOf(YearMonth.of(2026, 9)), months.map { it.month })
        assertTrue(months.single().days.all { it.isEmpty })
    }

    @Test
    fun `every day of a month is there, even the ones with nothing on them`() {
        val months = build(listOf(entry("2026-08-05T09:00", "2026-08-05T10:00", Element.WATER)))
        val august = months.first { it.month == YearMonth.of(2026, 8) }
        assertEquals(31, august.days.size)
        assertTrue(august.days.first { it.date == LocalDate.parse("2026-08-06") }.isEmpty)
    }

    @Test
    fun `a day counts its distinct elements and the time it really covered`() {
        val months = build(
            listOf(
                entry("2026-09-14T09:00", "2026-09-14T10:00", Element.FIRE),
                entry("2026-09-14T09:30", "2026-09-14T10:00", Element.FIRE),
                entry("2026-09-14T11:00", "2026-09-14T11:30", Element.WATER),
                entry("2026-09-14T14:00", "2026-09-14T14:15", null)
            )
        )
        val marked = day(months, "2026-09-14")
        assertEquals(2, marked.elementsTouched)
        // An hour and a half of tagged time, the overlap counted once, plus a quarter untagged.
        assertEquals(Duration.ofMinutes(105), marked.logged)
    }

    @Test
    fun `an entry crossing midnight marks both days with its own share`() {
        val months = build(listOf(entry("2026-09-12T23:30", "2026-09-13T00:30", Element.VOID)))
        assertEquals(Duration.ofMinutes(30), day(months, "2026-09-12").logged)
        assertEquals(Duration.ofMinutes(30), day(months, "2026-09-13").logged)
        assertEquals(1, day(months, "2026-09-13").elementsTouched)
    }
}
