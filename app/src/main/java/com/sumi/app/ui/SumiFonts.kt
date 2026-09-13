package com.sumi.app.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sumi.app.R

/**
 * Shippori Mincho, subset to Latin plus the five element kanji and 墨.
 *
 * In-app only. The widget cannot use it - RemoteViews has no way to set a
 * typeface, so the home screen falls back to the system serif - but Compose loads
 * app fonts directly.
 */
object SumiFonts {
    val mincho = FontFamily(
        Font(R.font.shippori_mincho_regular, FontWeight.Normal),
        Font(R.font.shippori_mincho_medium, FontWeight.Medium)
    )
}
