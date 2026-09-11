package com.beaver.app.widget

import android.graphics.Color

/**
 * Colours for the widget face.
 *
 * A frosted pane holds a grid of frosted day tiles and a row of frosted element
 * chips. Android cannot blur what sits behind a widget, so none of this is
 * optically frosted - it is milky translucent white with a lit top edge and a
 * specular sweep, which is what actually reads as frosted glass at this size.
 *
 * The pane is deliberately dimmer than the tiles that sit on it. Make them the
 * same milkiness and the tiles vanish into their own background.
 */
data class GlassTheme(
    // ---- backing pane ----
    val paneFill: Int,
    val paneSheenHigh: Int,
    val paneSheenLow: Int,
    val paneRim: Int,
    val paneTopHighlight: Int,

    // ---- day tiles ----
    val frostTop: Int,
    val frostBottom: Int,
    val frostEdge: Int,
    val glossTop: Int,
    val glossBottom: Int,
    val todayRing: Int,
    val futureCell: Int,

    // ---- element chips ----
    val chipTop: Int,
    val chipBottom: Int,
    val chipRim: Int,
    /** Ink for a kanji whose goal is done, sitting on its element colour. */
    val kanjiOnColor: Int,
    /** How much of the element colour fills a completed chip. */
    val chipFillAlpha: Int,
    /** How strongly an untouched chip shows its element colour. */
    val kanjiIdleAlpha: Int,
    /** Darkens an untouched chip so its glyph has contrast on any wallpaper. */
    val chipIdleScrim: Int,

    /** Index is the number of goals completed, 1..5; index 0 is unused. */
    val ramp: IntArray
) {
    /** Null means draw the frosted-glass treatment rather than a flat colour. */
    fun cellColor(completed: Int): Int? =
        if (completed <= 0) null else ramp[completed.coerceAtMost(ramp.lastIndex)]

    // Data classes compare IntArray by reference, so equality would be quietly
    // wrong without this.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlassTheme) return false
        return paneFill == other.paneFill &&
            frostTop == other.frostTop &&
            frostBottom == other.frostBottom &&
            chipTop == other.chipTop &&
            chipFillAlpha == other.chipFillAlpha &&
            ramp.contentEquals(other.ramp)
    }

    override fun hashCode(): Int = 31 * paneFill + ramp.contentHashCode()

    companion object {
        val Default = GlassTheme(
            paneFill = Color.argb(34, 255, 255, 255),
            paneSheenHigh = Color.argb(44, 255, 255, 255),
            paneSheenLow = Color.argb(0, 255, 255, 255),
            paneRim = Color.argb(56, 255, 255, 255),
            paneTopHighlight = Color.argb(120, 255, 255, 255),

            frostTop = Color.argb(88, 255, 255, 255),
            frostBottom = Color.argb(44, 255, 255, 255),
            frostEdge = Color.argb(76, 255, 255, 255),
            glossTop = Color.argb(86, 255, 255, 255),
            glossBottom = Color.argb(0, 255, 255, 255),
            todayRing = Color.argb(240, 255, 255, 255),
            futureCell = Color.argb(20, 255, 255, 255),

            chipTop = Color.argb(116, 255, 255, 255),
            chipBottom = Color.argb(58, 255, 255, 255),
            chipRim = Color.argb(90, 255, 255, 255),
            kanjiOnColor = Color.argb(255, 28, 22, 12),
            chipFillAlpha = 226,
            kanjiIdleAlpha = 240,
            chipIdleScrim = Color.argb(54, 18, 16, 12),

            // Pale cream through to rich gold. Two earlier attempts failed here:
            // mixing brown into the low end looked like dirt, and dropping the
            // alpha instead let the wallpaper show through, so a light day took
            // on whatever colour happened to be behind it and read muddy. Every
            // step now stays opaque enough to hold its own hue, and "less" is
            // expressed by blending toward cream rather than toward the
            // wallpaper.
            ramp = intArrayOf(
                Color.TRANSPARENT,             // 0 - unused, frosted tile is drawn
                Color.argb(152, 250, 238, 196),// 1 of 5
                Color.argb(182, 251, 228, 158),// 2
                Color.argb(208, 252, 218, 124),// 3
                Color.argb(230, 253, 208, 94), // 4
                Color.argb(252, 255, 198, 66)  // 5 - a full day
            )
        )
    }
}
