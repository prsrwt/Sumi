package com.sumi.app.widget

import android.content.Context
import com.sumi.app.sync.SheetsSync

/**
 * Everything that must happen after the log or the settings change, in one place.
 *
 * The widget redraws, and the rhythm re-arms - the next question is timed from
 * the latest entry, so a save, an edit, a delete or a new interval all move it.
 * And if Google Sheets is connected, the months that changed go out to it.
 * Every write path calls this rather than each remembering its own chores.
 */
object WidgetSync {

    suspend fun onEntriesChanged(context: Context) {
        val appContext = context.applicationContext
        // Redrawing also re-arms the rhythm, so the next question is timed from this change.
        SumiWidget.updateAll(appContext)
        SheetsSync.requestIfNeeded(appContext)
    }
}
