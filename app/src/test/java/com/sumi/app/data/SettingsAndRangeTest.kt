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
        val range = RangeSuggestion.suggest(at("2026-09-13T10:00"), at("2026-09-13T11:20:37"), interval)
        assertEquals(at("2026-09-13T10:00"), range.start)
        assertEquals(at("2026-09-13T11:20"), range.endInclusive) // truncated to the minute
    }

    @Test
    fun `does not reach back across a long gap such as a night's sleep`() {
        val range = RangeSuggestion.suggest(at("2026-09-12T22:00"), at("2026-09-13T08:00"), interval)
        assertEquals(at("2026-09-13T07:15"), range.start)
        assertEquals(at("2026-09-13T08:00"), range.endInclusive)
    }

    @Test
    fun `continues right up to the three hour cap`() {
        val range = RangeSuggestion.suggest(at("2026-09-13T08:00"), at("2026-09-13T11:00"), interval)
        assertEquals(at("2026-09-13T08:00"), range.start)
    }

    @Test
    fun `with nothing logged it offers one interval`() {
        val range = RangeSuggestion.suggest(null, at("2026-09-13T11:00"), interval)
        assertEquals(at("2026-09-13T10:15"), range.start)
    }

    @Test
    fun `a last entry ending in the future is not continued`() {
        val range = RangeSuggestion.suggest(at("2026-09-13T12:00"), at("2026-09-13T11:00"), interval)
        assertEquals(at("2026-09-13T10:15"), range.start)
    }
}
