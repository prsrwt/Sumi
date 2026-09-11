package com.beaver.app.widget

import java.time.LocalDate
import kotlin.math.min

/**
 * Where everything sits on the widget face, derived purely from its pixel size.
 *
 * A frosted pane fills the widget. Inside it sit thirty day tiles in a 10x3
 * block and a row of five element chips. Thirty was chosen over the old 182-tile
 * half-year because the grid had become a solid slab of colour, and shrinking it
 * buys the tile size and the chip size that make the kanji readable.
 *
 * Dropping the seven-row week structure is the cost: weekday patterns ("every
 * Tuesday is empty") are no longer visible. The trade was made deliberately.
 */
class FaceLayout(
    val widthPx: Int,
    val heightPx: Int,
    private val density: Float
) {
    val hairline: Float = kotlin.math.max(1f, density)

    // ---- the pane ----

    // Room outside the slab for its contact shadow to fall into.
    private val paneInset: Float = 7f * density
    val paneLeft: Float = paneInset
    val paneTop: Float = paneInset
    val paneRight: Float = widthPx - paneInset
    val paneBottom: Float = heightPx - paneInset
    val paneRadius: Float = 26f * density

    private val panePadding: Float = 13f * density

    private val innerLeft: Float = paneLeft + panePadding
    private val innerTop: Float = paneTop + panePadding
    private val innerWidth: Float = (paneRight - panePadding) - innerLeft
    private val innerHeight: Float = (paneBottom - panePadding) - innerTop

    // ---- bands ----

    val chipBandHeight: Float = innerHeight * 0.30f
    private val bandGap: Float = innerHeight * 0.13f
    private val gridBandHeight: Float = innerHeight - chipBandHeight - bandGap

    val chipBandTop: Float = innerTop + gridBandHeight + bandGap

    /**
     * Tiles are square, so the cell size is whichever of width and height binds
     * first; the block is then centred in whatever slack the other axis has.
     */
    val cell: Float = min(
        innerWidth / (COLUMNS + (COLUMNS - 1) * GAP_RATIO),
        gridBandHeight / (ROWS + (ROWS - 1) * GAP_RATIO)
    ).coerceAtLeast(1f)

    val gap: Float = cell * GAP_RATIO
    /** Half the cell: the day marks are circles, not rounded squares. */
    val cellRadius: Float = cell * 0.5f

    private val gridWidth: Float = COLUMNS * cell + (COLUMNS - 1) * gap
    private val gridHeight: Float = ROWS * cell + (ROWS - 1) * gap
    private val gridLeft: Float = innerLeft + (innerWidth - gridWidth) / 2
    private val gridTop: Float = innerTop + (gridBandHeight - gridHeight) / 2

    fun cellLeft(column: Int): Float = gridLeft + column * (cell + gap)

    fun cellTop(row: Int): Float = gridTop + row * (cell + gap)

    // ---- the five element chips ----

    val slotWidth: Float = innerWidth / GOAL_SLOTS
    val chipCenterY: Float = chipBandTop + chipBandHeight * 0.5f
    val chipRadius: Float = min(chipBandHeight * 0.38f, slotWidth * 0.26f)
    val kanjiSize: Float = chipRadius * 1.16f

    fun slotCenterX(index: Int): Float = innerLeft + slotWidth * (index + 0.5f)

    // ---- the same geometry in dp, for the tap targets layered over the bitmap ----

    val chipBandTopDp: Float = chipBandTop / density
    val chipBandHeightDp: Float = chipBandHeight / density
    val innerLeftDp: Float = innerLeft / density

    // ---- dates ----

    /**
     * Row-major, oldest first: index 0 is 29 days ago and the last cell is today.
     */
    fun dateAt(today: LocalDate, column: Int, row: Int): LocalDate {
        val index = row * COLUMNS + column
        return today.minusDays((DAYS - 1 - index).toLong())
    }

    companion object {
        const val COLUMNS = 10
        const val ROWS = 3
        const val DAYS = COLUMNS * ROWS
        const val GOAL_SLOTS = 5
        private const val GAP_RATIO = 0.46f

        /** Exactly the window the grid shows; nothing older is ever needed. */
        fun maxRange(today: LocalDate): ClosedRange<LocalDate> =
            today.minusDays((DAYS - 1).toLong())..today
    }
}
