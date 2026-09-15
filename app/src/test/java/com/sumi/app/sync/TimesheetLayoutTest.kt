package com.sumi.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

class TimesheetLayoutTest {

    private val september = YearMonth.of(2026, 9)

    @Test
    fun `a month's tab id is the month itself`() {
        assertEquals(202609, TimesheetLayout.sheetIdFor(september))
        assertTrue(TimesheetLayout.isMonthTab(202609))
        assertTrue(TimesheetLayout.isMonthTab(202612))
    }

    @Test
    fun `the starter tab and random ids are not month tabs`() {
        assertFalse(TimesheetLayout.isMonthTab(0))
        assertFalse(TimesheetLayout.isMonthTab(202613))
        assertFalse(TimesheetLayout.isMonthTab(1_873_450_612))
    }

    @Test
    fun `tabs are titled by month`() {
        assertEquals("September 2026", TimesheetLayout.tabTitle(september, Locale.ENGLISH))
    }

    @Test
    fun `a newer month goes before older months`() {
        val existing = listOf(Tab(202608, "August 2026", 0), Tab(202607, "July 2026", 1))
        assertEquals(0, TimesheetLayout.insertIndex(existing, september))
    }

    @Test
    fun `an older month goes between the months around it`() {
        val existing = listOf(Tab(202610, "October 2026", 0), Tab(202608, "August 2026", 1))
        assertEquals(1, TimesheetLayout.insertIndex(existing, september))
    }

    @Test
    fun `the oldest month goes after the last month, ahead of the user's own tabs`() {
        val existing = listOf(Tab(202610, "October 2026", 0), Tab(55, "My notes", 1))
        assertEquals(1, TimesheetLayout.insertIndex(existing, september))
    }

    @Test
    fun `with no month tabs yet the new one goes at the end`() {
        assertEquals(1, TimesheetLayout.insertIndex(listOf(Tab(0, "Sheet1", 0)), september))
    }

    @Test
    fun `a title the user already used gets a number`() {
        assertEquals("September 2026", TimesheetLayout.uniqueTitle("September 2026", listOf("Sheet1")))
        assertEquals(
            "September 2026 (3)",
            TimesheetLayout.uniqueTitle("September 2026", listOf("September 2026", "September 2026 (2)"))
        )
    }

    @Test
    fun `adding a month makes its tab with a frozen, bold header`() {
        val requests = TimesheetLayout.addMonthTab(september, listOf(Tab(0, "Sheet1", 0)), Locale.ENGLISH)
        assertEquals(2, requests.length())

        val properties = requests.getJSONObject(0).getJSONObject("addSheet").getJSONObject("properties")
        assertEquals(202609, properties.getInt("sheetId"))
        assertEquals("September 2026", properties.getString("title"))
        assertEquals(1, properties.getJSONObject("gridProperties").getInt("frozenRowCount"))

        val cells = requests.getJSONObject(1).getJSONObject("updateCells")
        assertEquals(202609, cells.getJSONObject("start").getInt("sheetId"))
        val values = cells.getJSONArray("rows").getJSONObject(0).getJSONArray("values")
        assertEquals(TimesheetLayout.HEADER.size, values.length())
        assertEquals("Date", values.getJSONObject(0).getJSONObject("userEnteredValue").getString("stringValue"))
        assertTrue(values.getJSONObject(0).getJSONObject("userEnteredFormat").getJSONObject("textFormat").getBoolean("bold"))
    }
}
