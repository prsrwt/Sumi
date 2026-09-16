package com.sumi.app.ui.account

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumi.app.data.Element
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.data.Preset
import com.sumi.app.data.Presets
import com.sumi.app.ui.setup.SetupViewModel
import com.sumi.app.ui.SumiFonts
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A turning wheel of ready-made fives, with the shape such a life tends to draw
 * changing as the wheel turns.
 *
 * Turning is not choosing. The names are only written when the button below is
 * pressed, so a stray scroll can never quietly rewrite five goals someone has
 * already named.
 */
@Composable
fun FiveChooser(
    setup: SetupViewModel,
    modifier: Modifier = Modifier,
    /** Fewer rows and a flatter drawing where the page cannot scroll. */
    visibleRows: Int = 5,
    previewAspect: Float = 1.45f,
    /** What each set gives up. Worth reading in You, too much to read on page 3. */
    showBlurb: Boolean = true,
    /**
     * Take whatever the wheel rests on, with no button to press. Only for the
     * introduction, where the five are still empty and there is a Next button
     * waiting: one choice, one press. It stands down the moment the names are
     * somebody's own writing, which is the one case worth a question.
     */
    applyOnSettle: Boolean = false
) {
    val goals by setup.goals.collectAsStateWithLifecycle()
    val names by setup.names.collectAsStateWithLifecycle()
    val current = names ?: return
    if (goals.size != GOAL_COUNT) return

    // A preset waiting for a yes, because it would write over names somebody wrote.
    var pending by remember { mutableStateOf<Preset?>(null) }

    // Where the wheel opens, and whether replacing needs asking at all. Five names
    // that came from a preset are not "yours" in any sense worth protecting, so
    // swapping one ready-made set for another just happens.
    val chosen = Presets.matching(goals, current)
    val theirOwn = chosen == null && current.any { it.isNotBlank() }
    val automatic = applyOnSettle && !theirOwn

    PresetPicker(
        startAt = Presets.all.indexOf(chosen).coerceAtLeast(0),
        onApply = { preset ->
            if (theirOwn) pending = preset else setup.applyPreset(preset)
        },
        onSettle = if (automatic) ({ preset -> setup.applyPreset(preset) }) else null,
        visibleRows = visibleRows,
        previewAspect = previewAspect,
        showBlurb = showBlurb,
        modifier = modifier
    )

    pending?.let { preset ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (preset.isBlank) "Clear your five?" else "Replace your five?") },
            text = {
                Text(
                    if (preset.isBlank) "The five names are emptied so you can write your own. Nothing you have logged is lost."
                    else "The names you have now are replaced by ${preset.title.lowercase()}. " +
                        "Nothing you have logged is lost: entries are kept by element, so your history keeps its shape."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    setup.applyPreset(preset)
                    pending = null
                }) { Text(if (preset.isBlank) "Clear" else "Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Keep mine") }
            }
        )
    }
}

@Composable
private fun PresetPicker(
    startAt: Int,
    onApply: (Preset) -> Unit,
    /** Set when resting on a name is the whole choice, which leaves no button. */
    onSettle: ((Preset) -> Unit)?,
    visibleRows: Int,
    previewAspect: Float,
    showBlurb: Boolean,
    modifier: Modifier = Modifier
) {
    val presets = Presets.all
    val bandInk = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val itemHeight = 44.dp
    // Opens on the set the five already came from, so the wheel says where you are.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // Where the wheel is standing, as a fraction: 2.4 means the third row has
    // passed the middle and the fourth is coming up. Measured from where the rows
    // actually are rather than from the scroll offset, so the row in the middle of
    // the window is always the one the wheel calls chosen, whichever end it is at.
    //
    // Held as state rather than read here: the drawing and the fading read it while
    // the wheel turns, which redraws them without composing anything again. Only
    // the chosen row below causes real work, and only when it changes.
    val position = remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val rows = info.visibleItemsInfo
            val nearest = rows.minByOrNull { abs(it.offset + it.size / 2f - middle) }
            if (nearest == null || nearest.size == 0) 0f
            else nearest.index + (middle - (nearest.offset + nearest.size / 2f)) / nearest.size
        }
    }
    val centre by remember {
        derivedStateOf { position.value.roundToInt().coerceIn(0, presets.lastIndex) }
    }

    // A light tick as each name passes the middle, the way a dial clicks under a
    // finger. Nothing marks the stop: the feel belongs to the turning. Android
    // silences it if haptics are turned off.
    LaunchedEffect(state) {
        snapshotFlow { centre }.drop(1).collect { view.wheelTick() }
    }

    // Where there is no button, the wheel coming to rest is the choice.
    if (onSettle != null) {
        LaunchedEffect(state) {
            snapshotFlow { state.isScrollInProgress }
                .drop(1)
                .filter { !it }
                .collect { onSettle(presets[centre]) }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShapePreview(
            preset = presets[centre],
            position = position,
            aspect = previewAspect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Two blank rows above and below, so the first and last names can
            // reach the middle of the wheel like any other.
            contentPadding = PaddingValues(vertical = itemHeight * (visibleRows / 2)),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleRows)
                .drawBehind {
                    // The window the wheel turns behind: two hairlines, so it is
                    // never a guess which name is the chosen one.
                    val band = size.height / visibleRows
                    val top = (size.height - band) / 2f
                    val line = 1.dp.toPx()
                    drawLine(bandInk, Offset(0f, top), Offset(size.width, top), line)
                    drawLine(bandInk, Offset(0f, top + band), Offset(size.width, top + band), line)
                }
        ) {
            items(presets.size) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        // Fading happens as the wheel turns, so it is set on the
                        // layer rather than composed: turning costs no recomposition.
                        .graphicsLayer {
                            alpha = (1f - abs(position.value - index) * 0.34f).coerceIn(0.25f, 1f)
                        }
                        .selectable(
                            selected = index == centre,
                            onClick = { scope.launch { state.animateScrollToItem(index) } }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = presets[index].title,
                        style = if (index == centre) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showBlurb) {
            Text(
                text = presets[centre].blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (onSettle == null) {
            OutlinedButton(
                onClick = {
                    view.wheelTick()
                    onApply(presets[centre])
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // "Clear" is a threat on the first morning, when there is nothing
                // to clear; naming them yourself is what the blank rows are for.
                Text(if (presets[centre].isBlank) "Name them myself" else "Use these five")
            }
        }
    }
}

/**
 * The pentagon a life like this tends to draw, with the preset's own names at the
 * tips. An example, never a target: what it is there to show is that every shape
 * leans, so nobody reads their own uneven pentagon as a failure.
 */
@Composable
private fun ShapePreview(
    preset: Preset,
    /** Read inside the drawing, so turning the wheel redraws without recomposing. */
    position: State<Float>,
    aspect: Float,
    modifier: Modifier = Modifier
) {
    val presets = Presets.all
    val ink = MaterialTheme.colorScheme.onBackground
    // Ten pieces of text are measured on every frame, more than the default cache
    // holds, which would throw every measurement away between frames.
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val kanjiStyle = TextStyle(fontFamily = SumiFonts.mincho, fontSize = 18.sp)
    val nameStyle = TextStyle(fontSize = 11.sp, color = ink)
    val description = if (preset.isBlank) "No example shape" else
        "An example of the shape ${preset.title} tends to draw"

    Canvas(
        modifier = modifier
            .aspectRatio(aspect)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.34f
        val elements = Element.entries

        fun vertex(index: Int, r: Float): Offset {
            val angle = -PI / 2 + index * 2 * PI / elements.size
            return Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
        }

        fun pentagon(r: Float) = Path().apply {
            elements.indices.forEach { i ->
                val p = vertex(i, r)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        val hairline = 1.dp.toPx()
        for (ring in 1..3) {
            drawPath(pentagon(radius * ring / 3), ink.copy(alpha = 0.10f), style = Stroke(hairline))
        }
        elements.indices.forEach { i ->
            drawLine(ink.copy(alpha = 0.10f), center, vertex(i, radius), hairline)
        }

        // Part way between the shapes either side of the middle, so the drawing
        // flows with the wheel rather than jumping at each name.
        val at = position.value
        val lower = floor(at).toInt().coerceIn(0, presets.lastIndex)
        val upper = (lower + 1).coerceAtMost(presets.lastIndex)
        val shape = Presets.shapeBetween(presets[lower], presets[upper], at - lower)

        val drawn = Path().apply {
            elements.indices.forEach { i ->
                val p = vertex(i, radius * (shape[elements[i]] ?: 0f))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(drawn, ink.copy(alpha = 0.08f))
        drawPath(drawn, ink.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx()))

        elements.forEachIndexed { i, element ->
            val labelCenter = vertex(i, radius * 1.46f)
            val kanji = measurer.measure(element.kanji, kanjiStyle.copy(color = Color(element.color)))
            val name = measurer.measure(
                text = preset.names[element].orEmpty(),
                style = nameStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (size.width * 0.3f).toInt())
            )
            var y = labelCenter.y - (kanji.size.height + name.size.height) / 2f
            for (line in listOf(kanji, name)) {
                drawText(line, topLeft = Offset(labelCenter.x - line.size.width / 2f, y))
                y += line.size.height
            }
        }
    }
}

/**
 * The tick of one name passing the middle of the wheel.
 *
 * The keyboard tap, deliberately: it is the lightest effect every phone actually
 * implements. The newer ticks meant for dials, and the clock tick, are silent on
 * some makers' software, and the confirm effect is a thump rather than a tick.
 */
private fun View.wheelTick() {
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
