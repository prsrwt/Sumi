package com.sumi.app.ui.balance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.Element
import com.sumi.app.data.Goal
import com.sumi.app.ui.Format
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiFonts
import java.time.Duration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BalanceScreen(
    modifier: Modifier = Modifier,
    viewModel: BalanceViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val window by viewModel.windowDays.collectAsStateWithLifecycle()
    val snapshot = state.snapshot ?: return
    val windows = listOf(7, 14)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassTabs(
            tabs = windows.map { "Last $it days" },
            selectedIndex = windows.indexOf(window).coerceAtLeast(0),
            onSelect = { viewModel.setWindow(windows[it]) },
            modifier = Modifier.fillMaxWidth()
        )

        if (snapshot.isEmpty) {
            Text(
                text = "Log a few things and the shape will fill in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            Pentagon(snapshot = snapshot, modifier = Modifier.fillMaxWidth())

            snapshot.quietElement?.let { quiet ->
                val name = state.goals.firstOrNull { it.element == quiet }?.displayName ?: quiet.displayName
                Text(
                    text = "$name has been quiet lately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Breakdown(snapshot = snapshot, goals = state.goals)
        }

        Text(
            text = "Last 30 days",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        MonthGrid(days = snapshot.days, modifier = Modifier.fillMaxWidth())
        Text(
            text = "Each dot fills with how many of your five got any time that day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "This shows where your energy has gone, so you can shift your focus " +
                "— not a scorecard to keep perfect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * Five spokes, one per element, each as long as that element's share of the time
 * relative to the most-used one. Drawn in ink with the element colours only on
 * the kanji and the vertices: a small spoke is simply small, never red or marked.
 */
@Composable
private fun Pentagon(snapshot: BalanceSnapshot, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val kanjiStyle = TextStyle(fontFamily = SumiFonts.mincho, fontSize = 22.sp)
    val hoursStyle = TextStyle(fontSize = 11.sp, color = muted)

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.30f
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

        val maxMinutes = snapshot.perElement.values.maxOf { it.toMinutes() }.coerceAtLeast(1)
        val shape = Path().apply {
            elements.forEachIndexed { i, element ->
                val share = snapshot.perElement.getValue(element).toMinutes().toFloat() / maxMinutes
                val p = vertex(i, radius * share)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(shape, ink.copy(alpha = 0.08f))
        drawPath(shape, ink.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))

        elements.forEachIndexed { i, element ->
            val time = snapshot.perElement.getValue(element)
            val share = time.toMinutes().toFloat() / maxMinutes
            if (!time.isZero) drawCircle(Color(element.color), 3.5.dp.toPx(), vertex(i, radius * share))

            val labelCenter = vertex(i, radius * 1.34f)
            val kanji = measurer.measure(element.kanji, kanjiStyle.copy(color = Color(element.color)))
            drawText(kanji, topLeft = Offset(labelCenter.x - kanji.size.width / 2f, labelCenter.y - kanji.size.height / 2f))

            val hours = measurer.measure(Format.duration(time), hoursStyle)
            drawText(
                hours,
                topLeft = Offset(labelCenter.x - hours.size.width / 2f, labelCenter.y + kanji.size.height / 2f)
            )
        }
    }
}

@Composable
private fun Breakdown(snapshot: BalanceSnapshot, goals: List<Goal>) {
    val maxMinutes = snapshot.perElement.values.maxOf { it.toMinutes() }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        goals.sortedBy { it.slot }.forEach { goal ->
            val time = snapshot.perElement[goal.element] ?: Duration.ZERO
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.element.kanji,
                    fontFamily = SumiFonts.mincho,
                    fontSize = 20.sp,
                    color = Color(goal.element.color),
                    modifier = Modifier.width(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.displayName, style = MaterialTheme.typography.bodyMedium)
                    // The element's own colour, dimmer and shorter when there is less
                    // of it - low time reads as quiet, not as a warning.
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(time.toMinutes().toFloat() / maxMinutes)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(goal.element.color).copy(alpha = 0.7f))
                        )
                    }
                }
                Text(
                    text = Format.duration(time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        if (!snapshot.untagged.isZero) {
            Text(
                text = "Untagged · ${Format.duration(snapshot.untagged)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
    }
}

/** Beaver's grid, kept: ten across, three down, today last and ringed. */
@Composable
private fun MonthGrid(days: List<DayMark>, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val columns = 10
    val rows = ((days.size + columns - 1) / columns).coerceAtLeast(1)

    Canvas(modifier = modifier.aspectRatio(columns.toFloat() / rows)) {
        val cell = size.width / columns
        val r = cell * 0.30f
        val hairline = 1.dp.toPx()

        days.forEachIndexed { index, day ->
            val center = Offset(cell * (index % columns) + cell / 2, cell * (index / columns) + cell / 2)
            if (day.elementsTouched == 0) {
                drawCircle(ink.copy(alpha = 0.14f), r, center, style = Stroke(hairline))
            } else {
                drawCircle(ink.copy(alpha = 0.16f + 0.16f * day.elementsTouched), r, center)
            }
            if (index == days.lastIndex) {
                drawCircle(ink.copy(alpha = 0.8f), r + hairline * 2.5f, center, style = Stroke(hairline))
            }
        }
    }
}
