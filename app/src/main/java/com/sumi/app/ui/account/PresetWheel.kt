package com.sumi.app.ui.account

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.sumi.app.data.Goal
import com.sumi.app.data.Preset
import com.sumi.app.data.Presets
import com.sumi.app.ui.SumiFonts
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Rows visible at once, with the chosen one in the middle. Odd, so there is a middle. */
private const val VISIBLE_ROWS = 5

/**
 * A turning wheel of ready-made fives, with the shape such a life tends to draw
 * changing as the wheel turns.
 *
 * Turning is not choosing. The names are only written when the button below is
 * pressed, so a stray scroll can never quietly rewrite five goals someone has
 * already named.
 */
@Composable
fun PresetPicker(
    goals: List<Goal>,
    onApply: (Preset) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = Presets.all
    val bandInk = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val itemHeight = 44.dp
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Where the wheel is standing, as a fraction: 2.4 means the third row has
    // passed the middle and the fourth is coming up. Measured from where the rows
    // actually are rather than from the scroll offset, so the row in the middle of
    // the window is always the one the wheel calls chosen, whichever end it is at.
    val position by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val rows = info.visibleItemsInfo
            val nearest = rows.minByOrNull { abs(it.offset + it.size / 2f - middle) }
            if (nearest == null || nearest.size == 0) 0f
            else nearest.index + (middle - (nearest.offset + nearest.size / 2f)) / nearest.size
        }
    }
    val centre = position.roundToInt().coerceIn(0, presets.lastIndex)
    val lower = floor(position).toInt().coerceIn(0, presets.lastIndex)
    val upper = (lower + 1).coerceAtMost(presets.lastIndex)
    val shape = Presets.shapeBetween(presets[lower], presets[upper], position - lower)

    // A small tick as each name passes the centre, the way a dial clicks.
    LaunchedEffect(state) {
        snapshotFlow { position.roundToInt() }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShapePreview(
            preset = presets[centre],
            shape = shape,
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
            contentPadding = PaddingValues(vertical = itemHeight * (VISIBLE_ROWS / 2)),
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * VISIBLE_ROWS)
                .drawBehind {
                    // The window the wheel turns behind: two hairlines, so it is
                    // never a guess which name is the chosen one.
                    val band = size.height / VISIBLE_ROWS
                    val top = (size.height - band) / 2f
                    val line = 1.dp.toPx()
                    drawLine(bandInk, Offset(0f, top), Offset(size.width, top), line)
                    drawLine(bandInk, Offset(0f, top + band), Offset(size.width, top + band), line)
                }
        ) {
            items(presets.size) { index ->
                val distance = abs(position - index)
                val fade = (1f - distance * 0.34f).coerceIn(0.25f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
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
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = fade),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = presets[centre].blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        OutlinedButton(
            onClick = { onApply(presets[centre]) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (presets[centre].isBlank) "Clear the five" else "Use these five")
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
    shape: Map<Element, Float>,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val measurer = rememberTextMeasurer()
    val kanjiStyle = TextStyle(fontFamily = SumiFonts.mincho, fontSize = 18.sp)
    val nameStyle = TextStyle(fontSize = 11.sp, color = ink)
    val description = if (preset.isBlank) "No example shape" else
        "An example of the shape ${preset.title} tends to draw"

    Canvas(
        modifier = modifier
            .aspectRatio(1.45f)
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
