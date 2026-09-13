package com.sumi.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * How milky the glass is. Pale, warm, paper-like - ink is always dark, so the
 * glass has to carry its own light for the text to read on any wallpaper.
 */
data class GlassStyle(
    val fill: Int,
    val border: Int,
    val grainAlpha: Int
) {
    companion object {
        /**
         * Over a dark wallpaper the glass must be milkier, or dark ink sinks into
         * what shows through behind it.
         */
        val OnDarkWallpaper = GlassStyle(
            fill = Color.argb(158, 250, 248, 244),
            border = Color.argb(96, 255, 255, 255),
            grainAlpha = 9
        )

        /** Over a pale wallpaper the ink already has contrast, so more shows through. */
        val OnLightWallpaper = GlassStyle(
            fill = Color.argb(118, 252, 250, 246),
            border = Color.argb(128, 255, 255, 255),
            grainAlpha = 7
        )
    }
}

/**
 * Draws the flat frosted slab the widget's text sits on.
 *
 * Deliberately flat. An earlier version added a contact shadow, an ambient halo,
 * a rim glint and a bevel to imitate a thick pane of glass, and the result read as
 * a raised object rather than a quiet surface. What remains is one even fill, one
 * even hairline edge, and faint grain - texture, not depth.
 *
 * Only the glass is drawn here. The text is left to real views on top, because
 * the clock has to be a system-ticked TextClock.
 */
object GlassRenderer {

    fun render(widthPx: Int, heightPx: Int, density: Float, style: GlassStyle): Bitmap {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hairline = maxOf(1f, density)
        val inset = 2f * density
        val radius = 28f * density
        val pane = RectF(inset, inset, width - inset, height - inset)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
        fill.color = style.fill
        canvas.drawRoundRect(pane, radius, radius, fill)

        val clip = Path().apply { addRoundRect(pane, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        fill.shader = grain
        fill.alpha = style.grainAlpha
        canvas.drawRect(pane, fill)
        canvas.restore()

        // One even hairline all the way round. No gradient, so no implied light.
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = hairline
            color = style.border
        }
        val edge = RectF(pane).apply { inset(hairline / 2, hairline / 2) }
        canvas.drawRoundRect(edge, radius, radius, stroke)

        return bitmap
    }

    /**
     * Tiled noise, generated once. A narrow brightness band on purpose: full-range
     * noise at device-pixel scale reads as television static, not a surface.
     */
    private val grain: BitmapShader by lazy {
        val size = 64
        val random = java.util.Random(7)
        val pixels = IntArray(size * size) { Color.argb(104 + random.nextInt(48), 255, 255, 255) }
        val noise = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        noise.setPixels(pixels, 0, size, 0, 0, size, size)
        BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}
