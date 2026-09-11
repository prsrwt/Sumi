package com.beaver.app.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where everything sits on the widget face, derived purely from its pixel size.
 *
 * There is no panel: the grid of tiles and the row of five marks beneath it are
 * the whole widget, floating directly on the wallpaper. Bands are allocated as
 * fractions of the height rather than fixed dp, so the face stays balanced at any
 * size the user drags to.
 */
class FaceLayout(
    val widthPx: Int,
    val heightPx: Int,
    private val density: Float
) {
    val hairline: Float = max(1f, density)

    private val padding: Float = 8f * density

    private val innerWidth: Float = widthPx - padding * 2
    private val innerHeight: Float = heightPx - padding * 2

    /** Vertical bands: grid, gap, marks. */
    val footerHeight: Float = innerHeight * 0.22f
    private val bandGap: Float = innerHeight * 0.07f
    private val gridHeight: Float = innerHeight - footerHeight - bandGap

    val gridTop: Float = padding
    val footerTop: Float = padding + gridHeight + bandGap

    /** Cell and gap solve 7*cell + 6*gap = gridHeight with gap = 24% of a cell. */
    val cell: Float = max(1f, gridHeight / (ROWS + (ROWS - 1) * GAP_RATIO))
    val gap: Float = cell * GAP_RATIO
    val cellRadius: Float = cell * 0.30f

    /**
     * As many whole weeks as fit, newest flush right. A hairline of clearance is
     * kept on the right so the ring around today is not clipped by the edge.
     */
    val columns: Int = min(
        MAX_COLUMNS,
        max(1, floor((innerWidth - hairline * 2 + gap) / (cell + gap)).toInt())
    )

    private val gridUsedWidth: Float = columns * (cell + gap) - gap
    private val gridStartX: Float = padding + innerWidth - hairline * 2 - gridUsedWidth

    fun cellLeft(column: Int): Float = gridStartX + column * (cell + gap)

    fun cellTop(row: Int): Float = gridTop + row * (cell + gap)

    // ---- the five marks ----

    val slotWidth: Float = innerWidth / GOAL_SLOTS
    val markCenterY: Float = footerTop + footerHeight * 0.5f
    val markRadius: Float = footerHeight * 0.26f

    /** Horizontal centre of the nth mark, spread evenly across the width. */
    fun slotCenterX(index: Int): Float = padding + slotWidth * (index + 0.5f)

    // ---- the same geometry in dp, for the tap targets layered over the bitmap ----
    //
    // The bitmap is drawn in pixels but Glance positions views in dp, so the two
    // have to be reconciled or the invisible tap targets drift off the marks.

    val paddingDp: Float = padding / density
    val footerTopDp: Float = footerTop / density
    val footerHeightDp: Float = (heightPx - padding - footerTop) / density

    // ---- dates ----

    /** Oldest date shown, given [columns] weeks ending with the week holding [today]. */
    fun firstDate(today: LocalDate): LocalDate =
        mondayOf(today).minusWeeks((columns - 1).toLong())

    fun dateAt(today: LocalDate, column: Int, row: Int): LocalDate =
        firstDate(today).plusWeeks(column.toLong()).plusDays(row.toLong())

    companion object {
        const val ROWS = 7
        const val GOAL_SLOTS = 5
        const val MAX_COLUMNS = 26
        private const val GAP_RATIO = 0.24f

        fun mondayOf(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Widest range any layout could ask for, fetched once so rendering stays synchronous. */
        fun maxRange(today: LocalDate): ClosedRange<LocalDate> {
            val start = mondayOf(today).minusWeeks((MAX_COLUMNS - 1).toLong())
            return start..mondayOf(today).plusDays(6)
        }
    }
}
