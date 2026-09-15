package com.sumi.app.sync

import android.content.Context
import com.sumi.app.data.SheetsLink
import com.sumi.app.data.SumiRepository
import java.time.YearMonth
import java.time.ZoneId

/** Joining Sumi to a sheet once Google has said yes, and letting go of it again. */
object SheetsConnection {

    /**
     * Finds the account's existing Sumi Timesheet or makes one, makes sure this
     * month has a tab, and only then records the link, so Setup never shows
     * "connected" to a sheet that doesn't exist.
     */
    suspend fun link(
        context: Context,
        repository: SumiRepository,
        accessToken: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): SheetsLink {
        val http = GoogleHttp(accessToken)
        val month = YearMonth.now(zone)

        val email = Timesheet.accountEmail(http)
        val spreadsheetId = Timesheet.find(http) ?: Timesheet.create(http, month)
        Timesheet.ensureMonthTab(http, spreadsheetId, month)

        return SheetsLink(
            accountEmail = email,
            spreadsheetId = spreadsheetId,
            lastSyncedAt = null,
            needsReconnect = false
        ).also {
            repository.saveSheetsLink(it)
            // A new connection, or a reconnect after time away: send everything.
            repository.markEveryMonthDirty()
            SheetsSync.schedulePeriodic(context)
            SheetsSync.request(context)
        }
    }

    /**
     * Sumi forgets the link; the sheet stays in the user's Drive, and Google's
     * permission is left in place. Revoking it here could cost Sumi its view of
     * the sheet it made, so a later reconnect would start a second sheet. Removing
     * the permission entirely is done from the Google Account's settings.
     */
    suspend fun unlink(context: Context, repository: SumiRepository) {
        SheetsSync.cancel(context)
        repository.clearSheetsLink()
    }
}
