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
 * Heights are allocated as fractions of the panel rather than fixed dp values, so
 * the face stays balanced whether the user sizes the widget at two rows or five.
 * Text sizes and the dot radius are then derived from those bands, which keeps
 * the proportions constant across sizes.
 */
class FaceLayout(
    val widthPx: Int,
    val heightPx: Int,
    private val density: Float
) {
    val cornerRadius: Float = 22f * density
    val hairline: Float = max(1f, density)

    private val padding: Float = 12f * density

    private val innerWidth: Float = widthPx - padding * 2
    private val innerHeight: Float = heightPx - padding * 2

    /** Vertical bands: header, gap, grid, gap, footer. */
    val headerHeight: Float = innerHeight * 0.16f
    val footerHeight: Float = innerHeight * 0.20f
    private val bandGap: Float = innerHeight * 0.05f
    private val gridHeight: Float = innerHeight - headerHeight - footerHeight - bandGap * 2

    val headerTop: Float = padding
    val gridTop: Float = padding + headerHeight + bandGap
    val footerTop: Float = gridTop + gridHeight + bandGap

    /** Cell and gap solve 7*cell + 6*gap = gridHeight with gap = 22% of a cell. */
    val cell: Float = max(1f, gridHeight / (ROWS + (ROWS - 1) * GAP_RATIO))
    val gap: Float = cell * GAP_RATIO
    val cellRadius: Float = cell * 0.28f

    /** Room for the single-letter weekday column, hidden when cells get tiny. */
    val showWeekdayLabels: Boolean = cell >= 5f * density
    val weekdayLabelWidth: Float = if (showWeekdayLabels) cell * 1.5f else 0f

    val gridLeft: Float = padding + weekdayLabelWidth
    // The today ring is drawn one hairline outside its cell, so the grid keeps a
    // hairline of clearance from the panel edge or the ring gets clipped.
    private val gridAvailableWidth: Float = innerWidth - weekdayLabelWidth - hairline * 2

    /** As many whole weeks as fit, newest flush to the right edge. */
    val columns: Int = min(
        MAX_COLUMNS,
        max(1, floor((gridAvailableWidth + gap) / (cell + gap)).toInt())
    )

    private val gridUsedWidth: Float = columns * (cell + gap) - gap
    val gridRight: Float = padding + innerWidth - hairline * 2
    private val gridStartX: Float = gridRight - gridUsedWidth

    val panelRight: Float = widthPx - padding
    val panelLeft: Float = padding
    val panelTop: Float = padding
    val panelBottom: Float = heightPx - padding

    // ---- text and dot sizing, derived from the bands ----

    val titleTextSize: Float = headerHeight * 0.74f
    val countTextSize: Float = headerHeight * 0.70f
    val weekdayTextSize: Float = cell * 0.82f
    val dotRadius: Float = footerHeight * 0.21f
    val dotLabelTextSize: Float = footerHeight * 0.28f
    val showDotLabels: Boolean = dotLabelTextSize >= 7f * density

    fun cellLeft(column: Int): Float = gridStartX + column * (cell + gap)

    fun cellTop(row: Int): Float = gridTop + row * (cell + gap)

    /** Horizontal centre of the nth goal dot, spread evenly across the panel. */
    fun dotCenterX(index: Int): Float {
        val slotWidth = innerWidth / GOAL_SLOTS
        return padding + slotWidth * (index + 0.5f)
    }

    val dotSlotWidth: Float = innerWidth / GOAL_SLOTS
    val dotCenterY: Float = footerTop + dotRadius + footerHeight * 0.06f

    // ---- the same geometry in dp, for the tap targets layered over the bitmap ----
    //
    // The bitmap is drawn in pixels but Glance positions views in dp, so the two
    // have to be reconciled or the invisible tap targets drift off the dots.

    /** Left and right inset of the row of five tap targets. */
    val paddingDp: Float = padding / density

    /** Distance from the top of the widget to the top of the footer band. */
    val footerTopDp: Float = footerTop / density

    /** Height of the footer band, which the tap targets fill. */
    val footerHeightDp: Float = (heightPx - padding - footerTop) / density

    /**
     * The oldest date the grid can show, given [columns] weeks ending with the
     * week that contains [today]. Column 0 row 0 is always a Monday.
     */
    fun firstDate(today: LocalDate): LocalDate = mondayOf(today).minusWeeks((columns - 1).toLong())

    fun dateAt(today: LocalDate, column: Int, row: Int): LocalDate =
        firstDate(today).plusWeeks(column.toLong()).plusDays(row.toLong())

    companion object {
        const val ROWS = 7
        const val GOAL_SLOTS = 5
        const val MAX_COLUMNS = 26
        private const val GAP_RATIO = 0.22f

        fun mondayOf(date: LocalDate): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Widest range any layout could ask for, fetched once so rendering stays synchronous. */
        fun maxRange(today: LocalDate): ClosedRange<LocalDate> {
            val start = mondayOf(today).minusWeeks((MAX_COLUMNS - 1).toLong())
            return start..mondayOf(today).plusDays(6)
        }
    }
}
