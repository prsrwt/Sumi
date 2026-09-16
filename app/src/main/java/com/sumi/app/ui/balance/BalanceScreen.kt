package com.sumi.app.ui.balance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import java.time.LocalDate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.Element
import com.sumi.app.data.Goal
import com.sumi.app.data.Untagged
import com.sumi.app.data.nameFor
import com.sumi.app.ui.Format
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiFonts
import java.time.Duration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BalanceScreen(
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BalanceViewModel = viewModel()
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
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

        // Shown even before anything is logged: the empty pentagon and the five at
        // 0m explain the screen from the first day, instead of a blank page.
        Pentagon(snapshot = snapshot, goals = state.goals, modifier = Modifier.fillMaxWidth())

        if (snapshot.isEmpty) {
            Text(
                text = "The shape fills in as you log.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        snapshot.quietElement?.let { quiet ->
            val name = state.goals.nameFor(quiet)
            Text(
                text = "$name has been quiet lately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        snapshot.heavy?.let { heavy ->
            val name = state.goals.nameFor(heavy.element)
            Text(
                text = if (heavy.longWeeks) "$name has been taking long weeks lately."
                else "$name has taken more than half your time lately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Breakdown(snapshot = snapshot, goals = state.goals)

        // The grid is a window on the whole log; tapping it opens the rest.
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { showHistory = true }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Last 30 days",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "All days →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            MonthGrid(days = snapshot.days, modifier = Modifier.fillMaxWidth())
            Text(
                text = "Each dot fills with how many of your five got any time that day. Today is ringed. " +
                    "Tap to look back through every day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "This shows where your energy has gone, so you can shift your focus. " +
                "It is not a scorecard to keep perfect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    if (showHistory) {
        HistorySheet(
            onOpenDay = {
                showHistory = false
                onOpenDay(it)
            },
            onDismiss = { showHistory = false }
        )
    }
}

/**
 * Five spokes, one per element, each as long as that element's share of the time
 * relative to the most-used one. Drawn in ink with the element colours only on
 * the kanji and the vertices: a small spoke is simply small, never red or marked.
 */
@Composable
private fun Pentagon(snapshot: BalanceSnapshot, goals: List<Goal>, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    // Ten labels are measured on each frame of the growing and reshaping, more
    // than the default cache holds, which would throw them all away every frame.
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val kanjiStyle = TextStyle(fontFamily = SumiFonts.mincho, fontSize = 22.sp)
    val nameStyle = TextStyle(fontSize = 12.sp, color = ink)

    val maxMinutes = snapshot.perElement.values.maxOf { it.toMinutes() }.coerceAtLeast(1)

    // Grows out from the centre each time Balance opens...
    val growth = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        growth.animateTo(1f, tween(GROW_MILLIS, easing = FastOutSlowInEasing))
    }
    // ...and each spoke glides to its new length when the window changes, rather
    // than the shape snapping. Five fixed calls in a fixed order, so each keeps
    // its own animation state across recompositions.
    val shares = Element.entries.map { element ->
        animateFloatAsState(
            targetValue = snapshot.perElement.getValue(element).toMinutes().toFloat() / maxMinutes,
            animationSpec = tween(RESHAPE_MILLIS, easing = FastOutSlowInEasing),
            label = "share-${element.name}"
        )
    }

    // The drawing read aloud: every goal and its time, in Setup's order.
    val description = "Balance over the last ${snapshot.windowDays} days. " + goals.sortedBy { it.slot }
        .joinToString(". ") { "${it.displayName}, ${Format.spokenDuration(snapshot.perElement.getValue(it.element))}" }

    Canvas(modifier = modifier.aspectRatio(1f).semantics { contentDescription = description }) {
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

        val shape = Path().apply {
            elements.indices.forEach { i ->
                val p = vertex(i, radius * shares[i].value * growth.value)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(shape, ink.copy(alpha = 0.08f))
        drawPath(shape, ink.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))

        elements.forEachIndexed { i, element ->
            val time = snapshot.perElement.getValue(element)
            if (!time.isZero) {
                drawCircle(Color(element.color), 3.5.dp.toPx(), vertex(i, radius * shares[i].value * growth.value))
            }

            // Kanji and the goal's own name, stacked and centred on the spoke's end.
            // No hours: the length of the spoke is the amount. Long names are cut
            // short rather than colliding.
            val labelCenter = vertex(i, radius * 1.40f)
            val kanji = measurer.measure(element.kanji, kanjiStyle.copy(color = Color(element.color)))
            val name = measurer.measure(
                text = goals.nameFor(element),
                style = nameStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (size.width * 0.28f).toInt())
            )
            var y = labelCenter.y - (kanji.size.height + name.size.height) / 2f
            for (line in listOf(kanji, name)) {
                drawText(line, topLeft = Offset(labelCenter.x - line.size.width / 2f, y))
                y += line.size.height
            }
        }
    }
}

@Composable
private fun Breakdown(snapshot: BalanceSnapshot, goals: List<Goal>) {
    // Untagged time shares the scale, so its bar is comparable with the five.
    val maxMinutes = (snapshot.perElement.values + snapshot.untagged).maxOf { it.toMinutes() }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        goals.sortedBy { it.slot }.forEach { goal ->
            BreakdownRow(
                kanji = goal.element.kanji,
                color = Color(goal.element.color),
                name = goal.displayName,
                time = snapshot.perElement[goal.element] ?: Duration.ZERO,
                maxMinutes = maxMinutes,
                key = goal.element.name
            )
        }

        if (!snapshot.untagged.isZero) {
            BreakdownRow(
                kanji = Untagged.KANJI,
                color = Color(Untagged.color),
                name = Untagged.NAME,
                time = snapshot.untagged,
                maxMinutes = maxMinutes,
                key = "untagged"
            )
        }
    }
}

@Composable
private fun BreakdownRow(kanji: String, color: Color, name: String, time: Duration, maxMinutes: Long, key: String) {
    val fraction by animateFloatAsState(
        targetValue = time.toMinutes().toFloat() / maxMinutes,
        animationSpec = tween(RESHAPE_MILLIS, easing = FastOutSlowInEasing),
        label = "bar-$key"
    )
    // Nothing on the row says the amount out loud, so the row says it for a screen
    // reader, which has no bar to look at.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$name, ${Format.spokenDuration(time)}"
        }
    ) {
        Text(
            text = kanji,
            fontFamily = SumiFonts.mincho,
            fontSize = 20.sp,
            color = color,
            // Decorative beside the name, which a screen reader reads instead.
            modifier = Modifier
                .width(32.dp)
                .clearAndSetSemantics {}
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
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
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = 0.7f))
                )
            }
        }
    }
}

/**
 * The last 30 days, ten across, oldest first and today last and ringed. Rolling
 * rather than a calendar month, so the grid never empties on the 1st; each dot
 * carries its day number, with the month's short name on the 1st, so any day
 * can be found by its date.
 */
@Composable
private fun MonthGrid(days: List<DayMark>, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val dayStyle = TextStyle(fontSize = 10.sp, color = muted)
    val todayStyle = dayStyle.copy(color = ink, fontWeight = FontWeight.SemiBold)

    val columns = 10
    val rows = ((days.size + columns - 1) / columns).coerceAtLeast(1)

    val touchedDays = days.count { it.elementsTouched > 0 }
    val description = "Last ${days.size} days. Your five had time on $touchedDays of them" +
        (days.lastOrNull()?.let { ". Today, ${it.elementsTouched} of five" } ?: "")

    Canvas(
        modifier = modifier
            .aspectRatio(columns / (rows * ROW_HEIGHT))
            .semantics { contentDescription = description }
    ) {
        val cell = size.width / columns
        val r = cell * 0.30f
        val hairline = 1.dp.toPx()

        days.forEachIndexed { index, day ->
            val isToday = index == days.lastIndex
            val center = Offset(
                cell * (index % columns) + cell / 2,
                cell * ROW_HEIGHT * (index / columns) + cell * 0.45f
            )
            if (day.elementsTouched == 0) {
                drawCircle(ink.copy(alpha = 0.14f), r, center, style = Stroke(hairline))
            } else {
                drawCircle(ink.copy(alpha = 0.16f + 0.16f * day.elementsTouched), r, center)
            }
            if (isToday) {
                drawCircle(ink.copy(alpha = 0.8f), r + hairline * 2.5f, center, style = Stroke(hairline))
            }

            val label = if (day.date.dayOfMonth == 1) SHORT_MONTH.format(day.date) else day.date.dayOfMonth.toString()
            val text = measurer.measure(label, if (isToday) todayStyle else dayStyle)
            drawText(
                text,
                topLeft = Offset(center.x - text.size.width / 2f, center.y + r + hairline * 4f)
            )
        }
    }
}

/** A grid row is a dot plus its date underneath, so rows are taller than they are wide. */
private const val ROW_HEIGHT = 1.45f
private val SHORT_MONTH = DateTimeFormatter.ofPattern("MMM")

private const val GROW_MILLIS = 700
private const val RESHAPE_MILLIS = 520
