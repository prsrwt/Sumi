package com.sumi.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sumi.app.ui.composer.ComposerActivity

/**
 * Placeholder face until the widget phase: proves a tap on the home screen opens
 * the composer. The glass, the live clock and the asking state replace this.
 */
class SumiWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xD9FAF8F4)))
                    .cornerRadius(26.dp)
                    .clickable(actionStartActivity<ComposerActivity>()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "What are you doing?",
                    style = TextStyle(color = ColorProvider(Color(0xFF1C1A17)), fontSize = 18.sp)
                )
            }
        }
    }
}
