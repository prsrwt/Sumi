package com.sumi.app.widget

import com.sumi.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class RhythmTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val quiet23to7 = Settings(Duration.ofMinutes(45), LocalTime.of(23, 0), LocalTime.of(7, 0))
    private val noQuiet = Settings(Duration.ofMinutes(45), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT)

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)

    @Test
    fun `next change is the ask when it comes before quiet hours`() {
        val next = Rhythm.nextChange(at("2026-09-13T10:00"), quiet23to7, at("2026-09-13T10:20"), utc)
        assertEquals(at("2026-09-13T10:45"), next)
    }

    @Test
    fun `quiet hours starting first win over a later ask`() {
        val next = Rhythm.nextChange(at("2026-09-13T22:40"), quiet23to7, at("2026-09-13T22:50"), utc)
        assertEquals(at("2026-09-13T23:00"), next)
    }

    @Test
    fun `during quiet hours the only change is quiet ending, even when overdue`() {
        val next = Rhythm.nextChange(at("2026-09-13T20:00"), quiet23to7, at("2026-09-14T02:00"), utc)
        assertEquals(at("2026-09-14T07:00"), next)
    }

    @Test
    fun `already asking outside quiet hours waits for quiet to begin`() {
        val next = Rhythm.nextChange(at("2026-09-13T09:00"), quiet23to7, at("2026-09-13T12:00"), utc)
        assertEquals(at("2026-09-13T23:00"), next)
    }

    @Test
    fun `with no quiet hours and nothing logged the next change is midnight`() {
        assertEquals(at("2026-09-14T00:00"), Rhythm.nextChange(null, noQuiet, at("2026-09-13T12:00"), utc))
    }

    @Test
    fun `midnight comes before a later ask, so the date turns over on time`() {
        val next = Rhythm.nextChange(at("2026-09-13T23:30"), noQuiet, at("2026-09-13T23:40"), utc)
        assertEquals(at("2026-09-14T00:00"), next)
    }

    @Test
    fun `during quiet hours midnight still redraws the date before quiet ends`() {
        val next = Rhythm.nextChange(at("2026-09-13T20:00"), quiet23to7, at("2026-09-13T23:30"), utc)
        assertEquals(at("2026-09-14T00:00"), next)
    }

    @Test
    fun `with no quiet hours the ask is the only change`() {
        val next = Rhythm.nextChange(at("2026-09-13T12:00"), noQuiet, at("2026-09-13T12:10"), utc)
        assertEquals(at("2026-09-13T12:45"), next)
    }

    @Test
    fun `a time already passed today resolves to tomorrow`() {
        val next = Rhythm.nextOccurrence(LocalTime.of(7, 0), at("2026-09-13T08:00"), utc)
        assertEquals(at("2026-09-14T07:00"), next)
    }

    @Test
    fun `a time that is exactly now counts as passed`() {
        val next = Rhythm.nextOccurrence(LocalTime.of(7, 0), at("2026-09-13T07:00"), utc)
        assertEquals(at("2026-09-14T07:00"), next)
    }

    @Test
    fun `a local time skipped by daylight saving still resolves to a real later instant`() {
        // 29 March 2026, London: clocks jump 01:00 -> 02:00, so 01:30 does not exist.
        val london = ZoneId.of("Europe/London")
        val now = LocalDateTime.parse("2026-03-29T00:30").atZone(london).toInstant()
        val next = Rhythm.nextOccurrence(LocalTime.of(1, 30), now, london)

        assertTrue(next.isAfter(now))
        assertEquals(LocalTime.of(2, 30), next.atZone(london).toLocalTime())
    }
}
