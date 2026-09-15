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
 * The glass and the ink that sits on it, chosen together for the wallpaper.
 *
 * The glass is thin on purpose, so the wallpaper shows through clearly. That puts
 * readability on the text rather than on a milky layer: near-black ink over light
 * wallpapers, and paper-white ink on a faint dark glass over dark ones. Milky glass
 * with dark ink on a dark wallpaper was the alternative, and it looked washed out.
 */
data class GlassStyle(
    val fill: Int,
    val border: Int,
    val grainAlpha: Int,
    /** The clock and the question. */
    val ink: Int,
    /** The date and the small clock: a little softer, still fully legible. */
    val inkMuted: Int
) {
    companion object {
        val OnDarkWallpaper = GlassStyle(
            fill = Color.argb(82, 18, 17, 16),
            border = Color.argb(56, 255, 255, 255),
            grainAlpha = 4,
            ink = Color.rgb(250, 248, 244),
            inkMuted = Color.argb(217, 250, 248, 244)
        )

        val OnLightWallpaper = GlassStyle(
            fill = Color.argb(56, 252, 250, 246),
            border = Color.argb(64, 255, 255, 255),
            grainAlpha = 4,
            ink = Color.rgb(17, 17, 17),
            inkMuted = Color.argb(217, 17, 17, 17)
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
        val pane = RectF(inset, inset, width - inset, height - inset)
        // At one row the slab is short; capping the radius at half its height
        // turns it into a clean pill instead of corners that overlap.
        val radius = minOf(28f * density, pane.height() / 2)

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
