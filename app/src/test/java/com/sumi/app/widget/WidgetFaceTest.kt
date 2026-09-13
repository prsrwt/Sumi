package com.sumi.app.widget

import com.sumi.app.data.Entry
import com.sumi.app.data.Prompts
import com.sumi.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class WidgetFaceTest {

    private val utc = ZoneOffset.UTC
    private val settings = Settings(Duration.ofMinutes(45), LocalTime.of(23, 0), LocalTime.of(7, 0))

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)

    private fun entryEndingAt(end: Instant) =
        Entry(id = 1, start = end.minusSeconds(1800), end = end, zone = utc, text = "work", element = null)

    @Test
    fun `nothing ever logged asks straight away`() {
        val face = WidgetFace.compute(null, settings, at("2026-09-13T12:00"), utc)
        assertEquals(WidgetFace.Asking(Prompts.DEFAULT), face)
    }

    @Test
    fun `rests until the interval has passed`() {
        val face = WidgetFace.compute(entryEndingAt(at("2026-09-13T12:00")), settings, at("2026-09-13T12:44"), utc)
        assertEquals(WidgetFace.Resting, face)
    }

    @Test
    fun `asks once the interval has passed, worded from when it became due`() {
        val due = at("2026-09-13T12:45")
        val face = WidgetFace.compute(entryEndingAt(at("2026-09-13T12:00")), settings, at("2026-09-13T12:45"), utc)
        assertEquals(WidgetFace.Asking(Prompts.questionFor(due)), face)
    }

    @Test
    fun `the wording does not change between redraws of the same ask`() {
        val latest = entryEndingAt(at("2026-09-13T12:00"))
        val first = WidgetFace.compute(latest, settings, at("2026-09-13T12:50"), utc)
        val later = WidgetFace.compute(latest, settings, at("2026-09-13T14:10"), utc)
        assertEquals(first, later)
    }

    @Test
    fun `never asks in quiet hours, however overdue`() {
        val face = WidgetFace.compute(entryEndingAt(at("2026-09-13T18:00")), settings, at("2026-09-14T03:00"), utc)
        assertEquals(WidgetFace.Resting, face)
    }
}
