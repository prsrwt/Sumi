package com.sumi.app.ui.today

import com.sumi.app.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class TimelineTest {

    private val dayStart = at("2026-09-13T00:00")
    private val dayEnd = at("2026-09-14T00:00")

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)

    private var nextId = 1L
    private fun entry(from: String, to: String) =
        Entry(nextId++, at(from), at(to), ZoneOffset.UTC, "x", null)

    @Test
    fun `a real gap between entries becomes an unlogged row`() {
        val rows = Timeline.build(
            listOf(entry("2026-09-13T09:00", "2026-09-13T10:00"), entry("2026-09-13T11:00", "2026-09-13T12:00")),
            dayStart, dayEnd, at("2026-09-13T12:00")
        )
        assertEquals(3, rows.size)
        assertEquals(TimelineRow.Unlogged(at("2026-09-13T10:00"), at("2026-09-13T11:00")), rows[1])
    }

    @Test
    fun `a few minutes between entries is just the day, not a gap`() {
        val rows = Timeline.build(
            listOf(entry("2026-09-13T09:00", "2026-09-13T10:00"), entry("2026-09-13T10:04", "2026-09-13T11:00")),
            dayStart, dayEnd, at("2026-09-13T11:00")
        )
        assertTrue(rows.none { it is TimelineRow.Unlogged })
    }

    @Test
    fun `time since the last entry shows as unlogged on today`() {
        val rows = Timeline.build(listOf(entry("2026-09-13T09:00", "2026-09-13T10:00")), dayStart, dayEnd, at("2026-09-13T11:30"))
        assertEquals(TimelineRow.Unlogged(at("2026-09-13T10:00"), at("2026-09-13T11:30")), rows.last())
    }

    @Test
    fun `a past day has no trailing gap up to now`() {
        val rows = Timeline.build(listOf(entry("2026-09-13T09:00", "2026-09-13T10:00")), dayStart, dayEnd, at("2026-09-15T11:30"))
        assertTrue(rows.none { it is TimelineRow.Unlogged })
    }

    @Test
    fun `the night before the first entry is never shown as a gap`() {
        val rows = Timeline.build(listOf(entry("2026-09-13T09:00", "2026-09-13T10:00")), dayStart, dayEnd, at("2026-09-13T10:00"))
        assertTrue(rows.first() is TimelineRow.Logged)
    }

    @Test
    fun `an entry crossing midnight is clipped to the day`() {
        val rows = Timeline.build(listOf(entry("2026-09-13T23:30", "2026-09-14T00:30")), dayStart, dayEnd, at("2026-09-14T09:00"))
        val logged = rows.single() as TimelineRow.Logged
        assertEquals(at("2026-09-14T00:00"), logged.shownEnd)
        assertEquals(Duration.ofMinutes(30), Timeline.loggedTotal(rows))
    }

    @Test
    fun `an entry that began the night before says where it really started`() {
        // "Now" is later that morning, so the timeline also holds a trailing gap row.
        val rows = Timeline.build(listOf(entry("2026-09-12T23:44", "2026-09-13T00:29")), dayStart, dayEnd, at("2026-09-13T09:00"))
        val spill = Timeline.spill(rows.filterIsInstance<TimelineRow.Logged>().single())
        assertEquals(Timeline.Spill.StartedEarlier(at("2026-09-12T23:44")), spill)
    }

    @Test
    fun `an entry running past midnight says where it really ends`() {
        val rows = Timeline.build(listOf(entry("2026-09-13T23:30", "2026-09-14T00:30")), dayStart, dayEnd, at("2026-09-14T09:00"))
        val spill = Timeline.spill(rows.single() as TimelineRow.Logged)
        assertEquals(Timeline.Spill.EndsLater(at("2026-09-14T00:30")), spill)
    }

    @Test
    fun `an entry inside its day has nothing to add`() {
        val rows = Timeline.build(listOf(entry("2026-09-13T09:00", "2026-09-13T10:00")), dayStart, dayEnd, at("2026-09-13T11:00"))
        assertNull(Timeline.spill(rows.first() as TimelineRow.Logged))
    }

    @Test
    fun `relative day words cover only the days either side of today`() {
        val today = LocalDate.parse("2026-09-13")
        assertEquals("yesterday", Timeline.relativeDay(LocalDate.parse("2026-09-12"), today))
        assertEquals("tomorrow", Timeline.relativeDay(LocalDate.parse("2026-09-14"), today))
        assertNull(Timeline.relativeDay(LocalDate.parse("2026-09-10"), today))
    }
}
