package com.sumi.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Everything that must happen after the log or the settings change, in one place.
 *
 * The widget redraws, and the rhythm re-arms - the next question is timed from
 * the latest entry, so a save, an edit, a delete or a new interval all move it.
 * Every write path calls this rather than each remembering its own chores.
 */
object WidgetSync {

    suspend fun onEntriesChanged(context: Context) {
        val appContext = context.applicationContext
        SumiWidget().updateAll(appContext)
        Rhythm.scheduleNext(appContext)
    }
}
