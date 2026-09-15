package com.sumi.app.sync

import com.sumi.app.data.Element
import com.sumi.app.data.Entry
import com.sumi.app.data.Goal
import com.sumi.app.data.SheetMonths
import com.sumi.app.data.Untagged
import com.sumi.app.data.nameFor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class SheetRowsTest {

    private val september = YearMonth.of(2026, 9)
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private val goals = listOf(
        Goal(0, "Workout", Element.FIRE),
        Goal(1, "", Element.WATER)
    )

    private fun entry(id: Long, from: String, to: String, text: String?, element: Element?, zone: ZoneId = ZoneOffset.UTC) =
        Entry(
            id = id,
            start = LocalDateTime.parse(from).atZone(zone).toInstant(),
            end = LocalDateTime.parse(to).atZone(zone).toInstant(),
            zone = zone,
            text = text,
            element = element
        )

    private fun JSONObject.cell(i: Int): JSONObject = getJSONArray("values").getJSONObject(i)
    private fun JSONObject.number(i: Int): Double = cell(i).getJSONObject("userEnteredValue").getDouble("numberValue")
    private fun JSONObject.text(i: Int): String? =
        cell(i).optJSONObject("userEnteredValue")?.getString("stringValue")

    private fun JSONArray.kinds(): List<String> = (0 until length()).map { getJSONObject(it).keys().next() }

    // ---- naming ----

    @Test
    fun `time is named by the goal, falling back to the element, and untagged has its own name`() {
        assertEquals("Workout", goals.nameFor(Element.FIRE))
        assertEquals("Water", goals.nameFor(Element.WATER))
        assertEquals(Untagged.NAME, goals.nameFor(null))
    }

    // ---- which tab ----

    @Test
    fun `an entry crossing into a new month stays on the month it started in`() {
        val start = LocalDateTime.parse("2026-09-30T23:30").atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(september, SheetMonths.of(start, "UTC"))
    }

    @Test
    fun `the month is read in the zone the entry was logged in`() {
        // 20:00 UTC on 30 September is already 1 October in Kolkata.
        val start = LocalDateTime.parse("2026-09-30T20:00").atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(YearMonth.of(2026, 10), SheetMonths.of(start, "Asia/Kolkata"))
    }

    @Test
    fun `month keys round trip`() {
        assertEquals("2026-09", SheetMonths.key(september))
        assertEquals(september, SheetMonths.parse("2026-09"))
    }

    // ---- rows ----

    @Test
    fun `a row holds real dates, times and minutes`() {
        val rows = TimesheetLayout.rows(listOf(entry(7, "2026-09-15T09:30", "2026-09-15T10:15", "Gym", Element.FIRE)), goals)
        val row = rows.getJSONObject(0)

        assertEquals(46_280.0, row.number(0), 0.0) // 15 Sep 2026 as a Sheets date
        assertEquals(9.5 / 24, row.number(1), 1e-9)
        assertEquals(10.25 / 24, row.number(2), 1e-9)
        assertEquals(45.0, row.number(3), 0.0)
        assertEquals("火 Fire", row.text(4))
        assertEquals("Workout", row.text(5))
        assertEquals("Gym", row.text(6))
        assertEquals(7.0, row.number(7), 0.0)
        assertEquals("DATE", row.cell(0).getJSONObject("userEnteredFormat").getJSONObject("numberFormat").getString("type"))
    }

    @Test
    fun `times are written in the entry's own zone`() {
        val rows = TimesheetLayout.rows(listOf(entry(1, "2026-09-15T09:30", "2026-09-15T10:00", "x", null, kolkata)), goals)
        assertEquals(9.5 / 24, rows.getJSONObject(0).number(1), 1e-9)
    }

    @Test
    fun `an untagged row says so and leaves the goal blank`() {
        val row = TimesheetLayout.rows(listOf(entry(1, "2026-09-15T09:00", "2026-09-15T10:00", "Reading", null)), goals)
            .getJSONObject(0)
        assertEquals("無 Untagged", row.text(4))
        assertEquals(null, row.text(5))
    }

    @Test
    fun `a note that looks like a formula stays plain text`() {
        val row = TimesheetLayout.rows(listOf(entry(1, "2026-09-15T09:00", "2026-09-15T10:00", "=SUM(A1:A9)", null)), goals)
            .getJSONObject(0)
        val value = row.cell(6).getJSONObject("userEnteredValue")
        assertEquals("=SUM(A1:A9)", value.getString("stringValue"))
        assertFalse(value.has("formulaValue"))
    }

    @Test
    fun `rows are oldest first`() {
        val rows = TimesheetLayout.rows(
            listOf(
                entry(2, "2026-09-15T11:00", "2026-09-15T12:00", "later", null),
                entry(1, "2026-09-15T09:00", "2026-09-15T10:00", "earlier", null)
            ),
            goals
        )
        assertEquals("earlier", rows.getJSONObject(0).text(6))
    }

    // ---- rewriting a month ----

    private val septemberTab = Tab(202609, "September 2026", 0)

    @Test
    fun `an existing tab is cleared below its header and rewritten`() {
        val requests = TimesheetLayout.rewriteMonth(
            september, listOf(entry(1, "2026-09-15T09:00", "2026-09-15T10:00", "x", null)), goals, listOf(septemberTab), Locale.ENGLISH
        )
        assertEquals(listOf("updateCells", "updateCells"), requests.kinds())

        val clear = requests.getJSONObject(0).getJSONObject("updateCells")
        assertFalse(clear.has("rows"))
        assertEquals(1, clear.getJSONObject("range").getInt("startRowIndex"))
        assertEquals(TimesheetLayout.HEADER.size, clear.getJSONObject("range").getInt("endColumnIndex"))
    }

    @Test
    fun `a month with no tab yet gets one before its rows`() {
        val requests = TimesheetLayout.rewriteMonth(
            september, listOf(entry(1, "2026-09-15T09:00", "2026-09-15T10:00", "x", null)), goals, emptyList(), Locale.ENGLISH
        )
        assertEquals(listOf("addSheet", "updateCells", "updateCells"), requests.kinds())
    }

    @Test
    fun `an emptied month clears its tab`() {
        val requests = TimesheetLayout.rewriteMonth(september, emptyList(), goals, listOf(septemberTab), Locale.ENGLISH)
        assertEquals(listOf("updateCells"), requests.kinds())
    }

    @Test
    fun `an empty month with no tab needs nothing`() {
        assertEquals(0, TimesheetLayout.rewriteMonth(september, emptyList(), goals, emptyList(), Locale.ENGLISH).length())
    }

    @Test
    fun `a month that outgrows its grid gets more rows first`() {
        val entries = (1..12L).map { entry(it, "2026-09-15T09:00", "2026-09-15T09:30", "x", null) }
        val requests = TimesheetLayout.rewriteMonth(september, entries, goals, listOf(septemberTab.copy(rowCount = 10)), Locale.ENGLISH)
        assertEquals(listOf("updateCells", "appendDimension", "updateCells"), requests.kinds())
        assertTrue(requests.getJSONObject(1).getJSONObject("appendDimension").getInt("length") >= 3)
    }
}
