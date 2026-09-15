package com.sumi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class SettingsAndRangeTest {

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
    private val interval = Duration.ofMinutes(45)

    // ---- quiet hours ----

    @Test
    fun `quiet hours that wrap midnight cover both sides of it`() {
        val s = Settings(interval, LocalTime.of(23, 0), LocalTime.of(7, 0))
        assertTrue(s.isQuiet(LocalTime.of(23, 0)))
        assertTrue(s.isQuiet(LocalTime.of(2, 0)))
        assertTrue(s.isQuiet(LocalTime.of(6, 59)))
        assertFalse(s.isQuiet(LocalTime.of(7, 0)))
        assertFalse(s.isQuiet(LocalTime.of(12, 0)))
        assertFalse(s.isQuiet(LocalTime.of(22, 59)))
    }

    @Test
    fun `quiet hours within one day are a plain range`() {
        val s = Settings(interval, LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertTrue(s.isQuiet(LocalTime.of(13, 30)))
        assertFalse(s.isQuiet(LocalTime.of(14, 0)))
        assertFalse(s.isQuiet(LocalTime.of(12, 59)))
    }

    @Test
    fun `equal start and end means no quiet hours at all`() {
        val s = Settings(interval, LocalTime.of(9, 0), LocalTime.of(9, 0))
        assertFalse(s.isQuiet(LocalTime.of(9, 0)))
        assertFalse(s.isQuiet(LocalTime.of(3, 0)))
    }

    // ---- the suggested range ----

    @Test
    fun `continues from the last entry when it ended recently`() {
        val range = RangeSuggestion.suggest(at("2026-09-13T09:00"), at("2026-09-13T10:00"), at("2026-09-13T11:20:37"), interval)
        assertEquals(at("2026-09-13T10:00"), range.start)
        assertEquals(at("2026-09-13T11:20"), range.endInclusive) // truncated to the minute
    }

    @Test
    fun `does not reach back across a long gap such as a night's sleep`() {
        val range = RangeSuggestion.suggest(at("2026-09-12T21:00"), at("2026-09-12T22:00"), at("2026-09-13T08:00"), interval)
        assertEquals(at("2026-09-13T07:15"), range.start)
        assertEquals(at("2026-09-13T08:00"), range.endInclusive)
    }

    @Test
    fun `continues right up to the three hour cap`() {
        val range = RangeSuggestion.suggest(at("2026-09-13T07:00"), at("2026-09-13T08:00"), at("2026-09-13T11:00"), interval)
        assertEquals(at("2026-09-13T08:00"), range.start)
    }

    @Test
    fun `with nothing logged it offers one interval`() {
        val range = RangeSuggestion.suggest(null, null, at("2026-09-13T11:00"), interval)
        assertEquals(at("2026-09-13T10:15"), range.start)
    }

    @Test
    fun `logging again in the same minute offers the stretch just logged`() {
        // The bug this replaces: the second log fell back to "45 minutes ago" and
        // could never be saved, because it collided with the entry just made.
        val range = RangeSuggestion.suggest(at("2026-09-13T10:13"), at("2026-09-13T10:58"), at("2026-09-13T10:58:20"), interval)
        assertEquals(at("2026-09-13T10:13"), range.start)
        assertEquals(at("2026-09-13T10:58"), range.endInclusive)
    }

    @Test
    fun `a last entry ending in the future offers that entry's stretch`() {
        val range = RangeSuggestion.suggest(at("2026-09-13T11:00"), at("2026-09-13T12:00"), at("2026-09-13T11:00"), interval)
        assertEquals(at("2026-09-13T11:00"), range.start)
        assertEquals(at("2026-09-13T12:00"), range.endInclusive)
    }

    // ---- overlapping time ----

    @Test
    fun `shared time is counted once`() {
        val covered = Intervals.covered(
            listOf(
                at("2026-09-13T09:00") to at("2026-09-13T10:00"),
                at("2026-09-13T09:15") to at("2026-09-13T09:20"),
                at("2026-09-13T09:50") to at("2026-09-13T10:30")
            )
        )
        assertEquals(Duration.ofMinutes(90), covered)
    }

    @Test
    fun `separate and touching stretches add up normally`() {
        val covered = Intervals.covered(
            listOf(
                at("2026-09-13T12:00") to at("2026-09-13T12:30"),
                at("2026-09-13T09:00") to at("2026-09-13T10:00"),
                at("2026-09-13T10:00") to at("2026-09-13T10:15")
            )
        )
        assertEquals(Duration.ofMinutes(105), covered)
    }

    // ---- picking a range by clock time ----

    private val utc = ZoneOffset.UTC

    @Test
    fun `a range within one day stays on that day`() {
        val range = TimeRange.resolve(LocalTime.of(10, 0), LocalTime.of(11, 30), at("2026-09-13T11:00"), utc)
        assertEquals(at("2026-09-13T10:00"), range.start)
        assertEquals(at("2026-09-13T11:30"), range.endInclusive)
    }

    @Test
    fun `a start after the end reads as starting the evening before`() {
        val range = TimeRange.resolve(LocalTime.of(23, 30), LocalTime.of(0, 15), at("2026-09-13T00:30"), utc)
        assertEquals(at("2026-09-12T23:30"), range.start)
        assertEquals(at("2026-09-13T00:15"), range.endInclusive)
    }

    @Test
    fun `pulling an end back across midnight lands on the evening before, not a day later`() {
        // The entry was 23:30 on the 13th to 00:15 on the 14th; the end is moved to 23:50.
        val range = TimeRange.resolve(LocalTime.of(23, 30), LocalTime.of(23, 50), at("2026-09-14T00:15"), utc)
        assertEquals(at("2026-09-13T23:30"), range.start)
        assertEquals(at("2026-09-13T23:50"), range.endInclusive)
    }

    @Test
    fun `the live length wraps past midnight`() {
        assertEquals(Duration.ofMinutes(45), TimeRange.length(LocalTime.of(23, 30), LocalTime.of(0, 15)))
        assertEquals(Duration.ofMinutes(90), TimeRange.length(LocalTime.of(10, 0), LocalTime.of(11, 30)))
    }
}
