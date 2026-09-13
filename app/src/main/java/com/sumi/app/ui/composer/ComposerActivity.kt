package com.sumi.app.ui.composer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sumi.app.ui.SumiTheme
import java.time.Instant

/**
 * The small sheet that opens when the widget is tapped.
 *
 * A separate activity rather than a screen inside the app, because a widget
 * cannot host a text field - RemoteViews has no input view at all. It uses its
 * own task and stays out of recents, so logging from the home screen never drags
 * the rest of the app forward.
 */
class ComposerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SumiTheme {
                ComposerScreen(onClose = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_ENTRY_ID = "sumi.entry_id"
        const val EXTRA_START = "sumi.start"
        const val EXTRA_END = "sumi.end"

        fun newEntry(context: Context): Intent = Intent(context, ComposerActivity::class.java)

        /** Opens pre-filled with a gap's bounds, for backfilling unlogged time. */
        fun backfill(context: Context, from: Instant, to: Instant): Intent =
            newEntry(context)
                .putExtra(EXTRA_START, from.toEpochMilli())
                .putExtra(EXTRA_END, to.toEpochMilli())

        fun edit(context: Context, entryId: Long): Intent =
            newEntry(context).putExtra(EXTRA_ENTRY_ID, entryId)
    }
}
