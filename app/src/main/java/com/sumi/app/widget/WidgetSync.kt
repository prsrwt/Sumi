package com.sumi.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Everything that must happen after the log changes, in one place.
 *
 * Today that is redrawing the widget. The asking rhythm hooks in here too, since
 * the next question is timed from the latest entry - so every save, edit and
 * delete path calls this rather than each remembering its own list of chores.
 */
object WidgetSync {

    suspend fun onEntriesChanged(context: Context) {
        SumiWidget().updateAll(context.applicationContext)
    }
}
