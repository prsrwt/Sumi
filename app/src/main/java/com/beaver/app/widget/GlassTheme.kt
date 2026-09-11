package com.beaver.app.widget

import android.graphics.Color

/**
 * Colours for the widget face.
 *
 * There is no panel any more - the grid itself is the widget. Each untouched day
 * is a small frosted tile, and days with goals ticked warm up through a yellow
 * ramp. Android cannot blur what is behind a widget, so "frosted" here is a pale
 * translucent fill with a highlight down its top edge, which is what actually
 * reads as glass at this size.
 */
data class GlassTheme(
    /** Top of the frosted tile gradient - the lit edge. */
    val frostTop: Int,
    /** Bottom of the frosted tile gradient. */
    val frostBottom: Int,
    /** Hairline around an untouched tile. */
    val frostEdge: Int,
    /** Gloss laid over a filled tile so it stays glassy rather than flat paint. */
    val glossTop: Int,
    val glossBottom: Int,
    /** Ring drawn around today. */
    val todayRing: Int,
    /** Days that have not happened yet. */
    val futureCell: Int,
    /** Kanji when its goal is still open. */
    val kanjiIdle: Int,
    /** Kanji when its goal is done. */
    val kanjiDone: Int,
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
        return frostTop == other.frostTop &&
            frostBottom == other.frostBottom &&
            frostEdge == other.frostEdge &&
            glossTop == other.glossTop &&
            glossBottom == other.glossBottom &&
            todayRing == other.todayRing &&
            futureCell == other.futureCell &&
            kanjiIdle == other.kanjiIdle &&
            kanjiDone == other.kanjiDone &&
            ramp.contentEquals(other.ramp)
    }

    override fun hashCode(): Int = 31 * frostTop + ramp.contentHashCode()

    companion object {
        val Default = GlassTheme(
            frostTop = Color.argb(64, 255, 255, 255),
            frostBottom = Color.argb(26, 255, 255, 255),
            frostEdge = Color.argb(54, 255, 255, 255),
            glossTop = Color.argb(72, 255, 255, 255),
            glossBottom = Color.argb(0, 255, 255, 255),
            todayRing = Color.argb(235, 255, 255, 255),
            futureCell = Color.argb(14, 255, 255, 255),
            kanjiIdle = Color.argb(115, 255, 255, 255),
            kanjiDone = Color.rgb(255, 214, 79),
            ramp = intArrayOf(
                Color.TRANSPARENT,       // 0 - unused, frosted tile is drawn instead
                Color.rgb(122, 95, 18),  // 1 of 5
                Color.rgb(168, 131, 15), // 2
                Color.rgb(217, 165, 20), // 3
                Color.rgb(242, 192, 39), // 4
                Color.rgb(255, 221, 87)  // 5 - a full day
            )
        )
    }
}
