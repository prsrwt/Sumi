package com.sumi.app.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/**
 * The calls that find, create and shape the Sumi Timesheet in the user's Drive.
 * What the sheet looks like lives in [TimesheetLayout]; this is only the talking.
 */
object Timesheet {

    private const val DRIVE = "https://www.googleapis.com/drive/v3"
    private const val SHEETS = "https://sheets.googleapis.com/v4/spreadsheets"

    fun url(spreadsheetId: String) = "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit"

    /** Which Google account granted access, to show in Setup. */
    suspend fun accountEmail(http: GoogleHttp): String =
        http.get("$DRIVE/about?fields=${GoogleHttp.encode("user(emailAddress)")}")
            .getJSONObject("user")
            .getString("emailAddress")

    /**
     * The existing Sumi Timesheet, if this account has one. drive.file only ever
     * shows Sumi its own files, so the marker search cannot pick up anything else.
     * The oldest wins, should there ever be two.
     */
    suspend fun find(http: GoogleHttp): String? {
        val files = http.get(
            "$DRIVE/files?q=${GoogleHttp.encode(TimesheetLayout.FIND_QUERY)}" +
                "&orderBy=createdTime&pageSize=1&spaces=drive&fields=${GoogleHttp.encode("files(id)")}"
        ).getJSONArray("files")
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    /**
     * Creates the spreadsheet with its marker, then swaps the blank starter tab
     * for this month's tab. A spreadsheet can never have zero tabs, so the new tab
     * is added before the starter one is removed, in the same batch.
     */
    suspend fun create(http: GoogleHttp, month: YearMonth): String {
        val file = http.post(
            "$DRIVE/files?fields=id",
            JSONObject()
                .put("name", TimesheetLayout.FILE_NAME)
                .put("mimeType", TimesheetLayout.SPREADSHEET_MIME)
                .put("appProperties", JSONObject().put(TimesheetLayout.MARKER_KEY, TimesheetLayout.MARKER_VALUE))
        )
        val id = file.getString("id")

        val starters = tabs(http, id)
        val requests = TimesheetLayout.addMonthTab(month, starters)
        starters.forEach { requests.put(TimesheetLayout.deleteTab(it.sheetId)) }
        batchUpdate(http, id, requests)
        return id
    }

    /** Adds the month's tab unless it is already there. */
    suspend fun ensureMonthTab(http: GoogleHttp, spreadsheetId: String, month: YearMonth) {
        val existing = tabs(http, spreadsheetId)
        if (existing.any { it.sheetId == TimesheetLayout.sheetIdFor(month) }) return
        batchUpdate(http, spreadsheetId, TimesheetLayout.addMonthTab(month, existing))
    }

    suspend fun tabs(http: GoogleHttp, spreadsheetId: String): List<Tab> {
        val sheets = http.get(
            "$SHEETS/$spreadsheetId?fields=" +
                GoogleHttp.encode("sheets.properties(sheetId,title,index,gridProperties.rowCount)")
        ).optJSONArray("sheets") ?: return emptyList()

        return (0 until sheets.length()).map { i ->
            val properties = sheets.getJSONObject(i).getJSONObject("properties")
            Tab(
                sheetId = properties.getInt("sheetId"),
                title = properties.getString("title"),
                index = properties.optInt("index", 0),
                rowCount = properties.optJSONObject("gridProperties")?.optInt("rowCount", DEFAULT_ROWS) ?: DEFAULT_ROWS
            )
        }
    }

    /**
     * Whether the sheet is still usable: it exists for this account and is not in
     * the bin. Sheets would happily keep writing to a binned file nobody can see.
     */
    suspend fun isLive(http: GoogleHttp, spreadsheetId: String): Boolean = try {
        !http.get("$DRIVE/files/$spreadsheetId?fields=trashed").optBoolean("trashed", false)
    } catch (e: GoogleHttp.HttpError) {
        if (e.code == 404) false else throw e
    }

    suspend fun batchUpdate(http: GoogleHttp, spreadsheetId: String, requests: JSONArray) {
        http.post("$SHEETS/$spreadsheetId:batchUpdate", JSONObject().put("requests", requests))
    }
}
