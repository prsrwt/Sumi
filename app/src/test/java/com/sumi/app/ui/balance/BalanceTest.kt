package com.sumi.app.ui.balance

import com.sumi.app.data.Element
import com.sumi.app.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BalanceTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-09-13")
    private val now = at("2026-09-13T20:00")

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)

    private var nextId = 1L
    private fun entry(from: String, to: String, element: Element?) =
        Entry(nextId++, at(from), at(to), utc, if (element == null) "untagged" else null, element)

    private fun compute(entries: List<Entry>, window: Int = 14) =
        Balance.compute(entries, today, now, utc, windowDays = window, gridDays = 30)

    @Test
    fun `time is summed per element and untagged time is kept apart`() {
        val snapshot = compute(
            listOf(
                entry("2026-09-13T09:00", "2026-09-13T10:00", Element.FIRE),
                entry("2026-09-12T09:00", "2026-09-12T09:30", Element.FIRE),
                entry("2026-09-13T11:00", "2026-09-13T11:45", null)
            )
        )
        assertEquals(Duration.ofMinutes(90), snapshot.perElement.getValue(Element.FIRE))
        assertEquals(Duration.ofMinutes(45), snapshot.untagged)
        assertEquals(Duration.ZERO, snapshot.perElement.getValue(Element.WATER))
    }

    @Test
    fun `the window rolls back from today and clips an entry that straddles its start`() {
        // A 7-day window starts at 00:00 on the 7th; this entry runs 23:00 on the 6th to 01:00 on the 7th.
        val snapshot = compute(listOf(entry("2026-09-06T23:00", "2026-09-07T01:00", Element.EARTH)), window = 7)
        assertEquals(Duration.ofHours(1), snapshot.perElement.getValue(Element.EARTH))
    }

    @Test
    fun `an element untouched for days while others are active is named, the emptiest first`() {
        val snapshot = compute(
            listOf(
                // Recent activity on Fire, Earth, Wind and Void...
                entry("2026-09-13T09:00", "2026-09-13T10:00", Element.FIRE),
                entry("2026-09-13T11:00", "2026-09-13T12:00", Element.EARTH),
                entry("2026-09-12T11:00", "2026-09-12T12:00", Element.WIND),
                entry("2026-09-12T13:00", "2026-09-12T14:00", Element.VOID),
                // ...while Water last had time a week ago.
                entry("2026-09-06T09:00", "2026-09-06T10:00", Element.WATER)
            )
        )
        assertEquals(Element.WATER, snapshot.quietElement)
    }

    @Test
    fun `nothing is called quiet when the user has simply been away`() {
        val snapshot = compute(listOf(entry("2026-09-05T09:00", "2026-09-05T10:00", Element.FIRE)))
        assertNull(snapshot.quietElement)
    }

    @Test
    fun `nothing is called quiet in the first days of use`() {
        // Only today's history exists, so the other four are "untouched" only vacuously.
        val snapshot = compute(listOf(entry("2026-09-13T09:00", "2026-09-13T10:00", Element.FIRE)))
        assertNull(snapshot.quietElement)
    }

    @Test
    fun `each grid day counts distinct elements, ignoring untagged time`() {
        val snapshot = compute(
            listOf(
                entry("2026-09-13T09:00", "2026-09-13T10:00", Element.FIRE),
                entry("2026-09-13T10:00", "2026-09-13T11:00", Element.FIRE),
                entry("2026-09-13T12:00", "2026-09-13T13:00", Element.WATER),
                entry("2026-09-13T14:00", "2026-09-13T15:00", null)
            )
        )
        assertEquals(30, snapshot.days.size)
        assertEquals(today, snapshot.days.last().date)
        assertEquals(2, snapshot.days.last().elementsTouched)
    }

    @Test
    fun `logging the same element twice for one stretch counts it once`() {
        val snapshot = compute(
            listOf(
                entry("2026-09-13T09:00", "2026-09-13T10:00", Element.WATER),
                entry("2026-09-13T09:30", "2026-09-13T10:00", Element.WATER)
            )
        )
        assertEquals(Duration.ofHours(1), snapshot.perElement.getValue(Element.WATER))
    }

    @Test
    fun `different elements sharing a stretch each get all of it`() {
        val snapshot = compute(
            listOf(
                entry("2026-09-13T09:00", "2026-09-13T10:00", Element.FIRE),
                entry("2026-09-13T09:15", "2026-09-13T09:20", Element.WATER)
            )
        )
        assertEquals(Duration.ofHours(1), snapshot.perElement.getValue(Element.FIRE))
        assertEquals(Duration.ofMinutes(5), snapshot.perElement.getValue(Element.WATER))
    }

    // ---- where you may be pushing too hard ----

    /** An hour of [element] on each of the last [days] days, from [hour] o'clock. */
    private fun daily(days: Int, element: Element, hours: Long, hour: Int = 9) = (0 until days).map { back ->
        val day = today.minusDays(back.toLong())
        val start = day.atTime(hour, 0).toInstant(utc)
        Entry(nextId++, start, start.plus(Duration.ofHours(hours)), utc, null, element)
    }

    @Test
    fun `a goal with more than half the time is named`() {
        val snapshot = compute(daily(6, Element.FIRE, hours = 3) + daily(6, Element.WATER, hours = 1, hour = 14))
        assertEquals(Heavy(Element.FIRE, longWeeks = false), snapshot.heavy)
    }

    @Test
    fun `exactly half is not more than half`() {
        val snapshot = compute(daily(6, Element.FIRE, hours = 2) + daily(6, Element.WATER, hours = 2, hour = 14))
        assertNull(snapshot.heavy)
    }

    @Test
    fun `too little logged to speak of shares says nothing`() {
        // Six hours of Fire against one of Water: a big share of very little.
        val snapshot = compute(daily(6, Element.FIRE, hours = 1) + listOf(entry("2026-09-13T15:00", "2026-09-13T16:00", Element.WATER)))
        assertNull(snapshot.heavy)
    }

    @Test
    fun `long weeks are named even when the share is under half`() {
        // 9 hours of Fire a day for a week is 63 hours; the others together get more.
        val snapshot = compute(
            daily(7, Element.FIRE, hours = 9, hour = 0) +
                daily(7, Element.WATER, hours = 5, hour = 10) +
                daily(7, Element.EARTH, hours = 5, hour = 16),
            window = 7
        )
        assertEquals(Heavy(Element.FIRE, longWeeks = true), snapshot.heavy)
    }

    @Test
    fun `nothing is called heavy in the first days of use`() {
        val snapshot = compute(listOf(entry("2026-09-13T01:00", "2026-09-13T19:00", Element.FIRE)))
        assertNull(snapshot.heavy)
    }
}
