package com.sumi.app.sync

import com.sumi.app.data.Element
import com.sumi.app.data.Entry
import com.sumi.app.data.Goal
import com.sumi.app.data.Untagged
import com.sumi.app.data.nameFor
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One tab of the spreadsheet as the Sheets API describes it. */
data class Tab(val sheetId: Int, val title: String, val index: Int, val rowCount: Int = DEFAULT_ROWS)

/** A new Sheets tab has 1000 rows until told otherwise. */
const val DEFAULT_ROWS = 1000

/**
 * The shape of the Sumi Timesheet, kept free of any network code so it can be
 * tested on its own.
 *
 * Each month is one tab, newest first. A tab is recognised by its sheetId rather
 * than its title, and the id is the month itself (September 2026 is 202609), so a
 * tab the user renames is still found and never duplicated.
 */
object TimesheetLayout {

    const val FILE_NAME = "Sumi Timesheet"
    const val SPREADSHEET_MIME = "application/vnd.google-apps.spreadsheet"

    /** A hidden tag on the file, so Sumi can find its sheet again after a reinstall. */
    const val MARKER_KEY = "sumi"
    const val MARKER_VALUE = "timesheet"

    val HEADER = listOf(
        "Date", "Start", "End", "Minutes", "Element", "Goal", "Part of life", "Word", "Note", "Sumi ID"
    )

    /** Drive search for the sheet: Sumi's marker, and not in the bin. */
    val FIND_QUERY = "appProperties has { key='$MARKER_KEY' and value='$MARKER_VALUE' } and trashed = false"

    fun sheetIdFor(month: YearMonth): Int = month.year * 100 + month.monthValue

    fun isMonthTab(sheetId: Int): Boolean =
        sheetId in 190_001..999_912 && sheetId % 100 in 1..12

    fun tabTitle(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month)

    /**
     * Where a new month's tab goes so month tabs stay newest first: just before the
     * first existing month that is older, or after the last month tab if none is.
     * Tabs the user added themselves keep their places.
     */
    fun insertIndex(existing: List<Tab>, month: YearMonth): Int {
        val id = sheetIdFor(month)
        val ordered = existing.sortedBy { it.index }
        ordered.firstOrNull { isMonthTab(it.sheetId) && it.sheetId < id }?.let { return it.index }
        val lastMonth = ordered.lastOrNull { isMonthTab(it.sheetId) }
        return if (lastMonth != null) lastMonth.index + 1 else ordered.size
    }

    /** Sheets refuses two tabs with one title; a tab the user made might already use it. */
    fun uniqueTitle(desired: String, taken: Collection<String>): String {
        if (desired !in taken) return desired
        var n = 2
        while ("$desired ($n)" in taken) n++
        return "$desired ($n)"
    }

    /**
     * The batchUpdate requests that add one month's tab: the tab with its header
     * row frozen, then the header itself in bold.
     */
    fun addMonthTab(month: YearMonth, existing: List<Tab>, locale: Locale = Locale.getDefault()): JSONArray {
        val sheetId = sheetIdFor(month)
        val title = uniqueTitle(tabTitle(month, locale), existing.map { it.title })

        val addSheet = JSONObject().put(
            "addSheet", JSONObject().put(
                "properties", JSONObject()
                    .put("sheetId", sheetId)
                    .put("title", title)
                    .put("index", insertIndex(existing, month))
                    .put("gridProperties", JSONObject().put("frozenRowCount", 1))
            )
        )

        val headerCells = headerCells()

        val header = JSONObject().put(
            "updateCells", JSONObject()
                .put("start", JSONObject().put("sheetId", sheetId).put("rowIndex", 0).put("columnIndex", 0))
                .put("rows", JSONArray().put(JSONObject().put("values", headerCells)))
                .put("fields", "userEnteredValue,userEnteredFormat.textFormat.bold")
        )

        return JSONArray().put(addSheet).put(header)
    }

    fun deleteTab(sheetId: Int): JSONObject =
        JSONObject().put("deleteSheet", JSONObject().put("sheetId", sheetId))

    // ---- rows ----

    /** Sheets counts days from 30 December 1899; 1 January 1970 is day 25569. */
    private const val SHEETS_EPOCH_OFFSET = 25_569.0
    private const val SECONDS_PER_DAY = 86_400.0

    /** How many columns Sumi owns. Anything the user puts from column I onwards is never touched. */
    val OWNED_COLUMNS = HEADER.size

    /**
     * One sheet row per entry, oldest first, in the entry's own time zone.
     *
     * Dates and times go in as real Sheets numbers with a display format, so they
     * sort, filter and add up. Notes go in as plain strings, never as typed input:
     * a note like "=SUM(A1)" or "1/2" stays exactly that text instead of turning
     * into a formula or a date.
     */
    fun rows(
        entries: List<Entry>,
        goals: List<Goal>,
        domains: Map<Long, String> = emptyMap(),
        words: Map<Long, String> = emptyMap()
    ): JSONArray {
        val rows = JSONArray()
        entries.sortedWith(compareBy({ it.start }, { it.id })).forEach { entry ->
            val start = entry.start.atZone(entry.zone)
            val end = entry.end.atZone(entry.zone)
            val cells = JSONArray()
                .put(numberCell(start.toLocalDate().toEpochDay() + SHEETS_EPOCH_OFFSET, "DATE", "yyyy-mm-dd"))
                .put(numberCell(start.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY, "TIME", "hh:mm"))
                .put(numberCell(end.toLocalTime().toSecondOfDay() / SECONDS_PER_DAY, "TIME", "hh:mm"))
                .put(numberCell(ChronoUnit.MINUTES.between(entry.start, entry.end).toDouble()))
                .put(textCell(elementLabel(entry.element)))
                .put(textCell(entry.element?.let { goals.nameFor(it) }))
                .put(textCell(entry.domainId?.let { domains[it] }))
                .put(textCell(entry.activityId?.let { words[it] }))
                .put(textCell(entry.text))
                .put(numberCell(entry.id.toDouble()))
            rows.put(JSONObject().put("values", cells))
        }
        return rows
    }

    fun elementLabel(element: Element?): String =
        if (element == null) "${Untagged.KANJI} ${Untagged.NAME}" else "${element.kanji} ${element.displayName}"

    /**
     * Everything that makes one month's tab match the phone, as a single batch -
     * which Sheets applies all or nothing, so a tab is never left half-written.
     *
     * The tab is created if missing; otherwise Sumi's columns below the header are
     * cleared and written afresh, with rows added first if the month has outgrown
     * the grid. Returns an empty batch for a month with no entries and no tab.
     */
    fun rewriteMonth(
        month: YearMonth,
        entries: List<Entry>,
        goals: List<Goal>,
        existing: List<Tab>,
        locale: Locale = Locale.getDefault(),
        domains: Map<Long, String> = emptyMap(),
        words: Map<Long, String> = emptyMap()
    ): JSONArray {
        val sheetId = sheetIdFor(month)
        val tab = existing.firstOrNull { it.sheetId == sheetId }
        val requests = JSONArray()

        if (tab == null) {
            if (entries.isEmpty()) return requests
            addMonthTab(month, existing, locale).let { add -> (0 until add.length()).forEach { requests.put(add.get(it)) } }
        } else {
            // The header too, not only the rows: a tab written by an older version
            // has fewer columns, and a rewrite that left the old header in place
            // would put the new columns under the wrong names.
            requests.put(
                JSONObject().put(
                    "updateCells", JSONObject()
                        .put("start", JSONObject().put("sheetId", sheetId).put("rowIndex", 0).put("columnIndex", 0))
                        .put("rows", JSONArray().put(JSONObject().put("values", headerCells())))
                        .put("fields", "userEnteredValue,userEnteredFormat.textFormat.bold")
                )
            )
            requests.put(
                JSONObject().put(
                    "updateCells", JSONObject()
                        .put(
                            "range", JSONObject()
                                .put("sheetId", sheetId)
                                .put("startRowIndex", 1)
                                .put("startColumnIndex", 0)
                                .put("endColumnIndex", OWNED_COLUMNS)
                        )
                        .put("fields", ROW_FIELDS)
                )
            )
        }

        val rowCount = tab?.rowCount ?: DEFAULT_ROWS
        val needed = entries.size + 1
        if (needed > rowCount) {
            requests.put(
                JSONObject().put(
                    "appendDimension", JSONObject()
                        .put("sheetId", sheetId)
                        .put("dimension", "ROWS")
                        .put("length", needed - rowCount + GROWTH_ROWS)
                )
            )
        }

        if (entries.isNotEmpty()) {
            requests.put(
                JSONObject().put(
                    "updateCells", JSONObject()
                        .put("start", JSONObject().put("sheetId", sheetId).put("rowIndex", 1).put("columnIndex", 0))
                        .put("rows", rows(entries, goals, domains, words))
                        .put("fields", ROW_FIELDS)
                )
            )
        }
        return requests
    }

    /** The header row, bold, as Sheets wants it. */
    private fun headerCells(): JSONArray {
        val cells = JSONArray()
        HEADER.forEach { name ->
            cells.put(
                JSONObject()
                    .put("userEnteredValue", JSONObject().put("stringValue", name))
                    .put(
                        "userEnteredFormat",
                        JSONObject().put("textFormat", JSONObject().put("bold", true))
                    )
            )
        }
        return cells
    }

    private const val ROW_FIELDS = "userEnteredValue,userEnteredFormat.numberFormat"
    private const val GROWTH_ROWS = 200

    private fun numberCell(value: Double, type: String? = null, pattern: String? = null): JSONObject {
        val cell = JSONObject().put("userEnteredValue", JSONObject().put("numberValue", value))
        if (type != null) {
            cell.put(
                "userEnteredFormat",
                JSONObject().put("numberFormat", JSONObject().put("type", type).put("pattern", pattern))
            )
        }
        return cell
    }

    /** A blank cell for nothing, so clearing and rewriting leaves no stale text behind. */
    private fun textCell(value: String?): JSONObject =
        if (value.isNullOrEmpty()) JSONObject()
        else JSONObject().put("userEnteredValue", JSONObject().put("stringValue", value))
}
