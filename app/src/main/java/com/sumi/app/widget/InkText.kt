package com.sumi.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.sumi.app.R
import kotlin.math.ceil

/**
 * Words for the widget, drawn in the app's own Mincho.
 *
 * A widget's views are drawn by the launcher from RemoteViews, which has no way to
 * set a typeface, so any TextView there falls back to a system font (and on some
 * phones, the maker's own). Words that only change when the widget redraws - the
 * question and the date - are therefore drawn here into small images instead.
 * The clock is the exception: it has to stay a system-ticked TextClock.
 */
object InkText {

    /**
     * A touch of stroke on top of Mincho Medium. Mincho's hairlines are delicate;
     * over a wallpaper they read better with slightly more weight than the
     * heaviest cut bundled with the app.
     */
    private const val EXTRA_WEIGHT = 0.022f

    @Volatile
    private var mincho: Typeface? = null

    fun render(
        context: Context,
        text: String,
        sizePx: Float,
        color: Int,
        maxWidthPx: Int,
        maxLines: Int
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            this.color = color
            typeface = typefaceFor(context, text, this)
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = sizePx * EXTRA_WEIGHT
        }

        // The stroke spreads each glyph slightly, so leave it room at the edges.
        val bleed = ceil(paint.strokeWidth).toInt() + 1
        val natural = ceil(StaticLayout.getDesiredWidth(text, paint)).toInt() + bleed * 2
        val width = natural.coerceIn(1, maxWidthPx.coerceAtLeast(1))

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val bitmap = Bitmap.createBitmap(width, (layout.height + bleed * 2).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            translate(0f, bleed.toFloat())
            layout.draw(this)
        }
        return bitmap
    }

    /**
     * The bundled Mincho is cut down to the characters Sumi uses. Anything outside
     * it, such as a month name in another language, falls back to the system serif
     * rather than drawing empty boxes.
     */
    private fun typefaceFor(context: Context, text: String, paint: Paint): Typeface {
        val font = mincho ?: (ResourcesCompat.getFont(context, R.font.shippori_mincho_medium) ?: Typeface.SERIF)
            .also { mincho = it }
        paint.typeface = font
        return if (text.all { it.isWhitespace() || paint.hasGlyph(it.toString()) }) font else Typeface.SERIF
    }
}
