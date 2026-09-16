package com.sumi.app.ui.balance

import androidx.compose.foundation.Canvas
import java.time.Duration
import com.sumi.app.data.nameFor
import com.sumi.app.data.Untagged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.ui.Format
import com.sumi.app.ui.SumiFonts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The whole log, in two views inside one sheet: month calendars you can scroll
 * back through, and one day's entries. Tapping a day opens it here rather than
 * throwing you onto another screen, so your place in the calendars is kept.
 * Tapping an entry then opens that day on Today, where it can be edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    onOpenDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val months by viewModel.months.collectAsStateWithLifecycle()
    val openDay by viewModel.openDay.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val weekdays = weekdayOrder(locale)

    ModalBottomSheet(
        // Back, and a swipe down, step out of a day first and only then close the
        // sheet. The sheet's own back handling runs before any handler put inside
        // it, so this is where that step has to happen. Closing always leaves the
        // sheet on the calendars, so reopening never lands on a day read days ago.
        onDismissRequest = {
            if (openDay != null) {
                viewModel.backToCalendar()
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = openDay,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally(tween(SLIDE_MILLIS)) { it / 4 } + fadeIn(tween(SLIDE_MILLIS)) togetherWith
                        fadeOut(tween(FADE_MILLIS))
                } else {
                    slideInHorizontally(tween(SLIDE_MILLIS)) { -it / 4 } + fadeIn(tween(SLIDE_MILLIS)) togetherWith
                        fadeOut(tween(FADE_MILLIS))
                }
            },
            label = "history"
        ) { date ->
            if (date == null) {
                Calendars(months, weekdays, locale, today, viewModel::open)
            } else {
                DayDetail(
                    day = day,
                    locale = locale,
                    onBack = viewModel::backToCalendar,
                    onOpenInToday = {
                        viewModel.backToCalendar()
                        onOpenDay(date)
                    }
                )
            }
        }
    }
}

@Composable
private fun Calendars(
    months: List<HistoryMonth>,
    weekdays: List<DayOfWeek>,
    locale: Locale,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    LazyColumn(
            // About two thirds of the screen: enough for a month at a glance,
            // little enough that the sheet still reads as a sheet.
            modifier = Modifier
                .fillMaxHeight(0.68f)
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your days", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Every day you have logged. Tap one to see what it held.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(months.size, key = { months[it].month.toString() }) { index ->
                MonthCalendar(
                    month = months[index],
                    weekdays = weekdays,
                    locale = locale,
                    today = today,
                    onOpenDay = onOpenDay
                )
            }
    }
}

/**
 * One day inside the sheet: what was logged, newest first. Read only here, so
 * nothing can be changed by accident while looking back; tapping a row opens the
 * day on Today, where entries are edited and gaps filled.
 */
@Composable
private fun DayDetail(
    day: HistoryDayDetail,
    locale: Locale,
    onBack: () -> Unit,
    onOpenInToday: () -> Unit
) {
    val context = LocalContext.current
    val date = day.date ?: return
    val title = remember(date, locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date) }

    Column(
        modifier = Modifier
            .fillMaxHeight(0.68f)
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the calendar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (day.total.isZero) "Nothing logged" else "${Format.duration(day.total)} logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenInToday) { Text("Open") }
        }

        if (day.rows.isEmpty()) {
            Text(
                text = "This day has nothing on it. Open it to fill something in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 20.dp)
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 40.dp)
        ) {
            items(day.rows.size, key = { day.rows[it].entry.id }) { index ->
                val row = day.rows[index]
                val element = row.entry.element
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenInToday)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(72.dp)) {
                        Text(Format.time(context, row.shownStart), style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = Format.time(context, row.shownEnd),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = element?.kanji ?: Untagged.KANJI,
                        fontFamily = SumiFonts.mincho,
                        fontSize = 20.sp,
                        color = Color(element?.color ?: Untagged.color),
                        modifier = Modifier.width(34.dp)
                    )
                    Text(
                        text = row.entry.text ?: day.goals.nameFor(element),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Format.duration(Duration.between(row.shownStart, row.shownEnd)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: HistoryMonth,
    weekdays: List<DayOfWeek>,
    locale: Locale,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val title = remember(month.month, locale) {
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month.month)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontFamily = SumiFonts.mincho,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {}
                )
            }
        }

        // A blank space for the days of the week before the 1st falls.
        val leading = weekdays.indexOf(month.month.atDay(1).dayOfWeek)
        val cells = List(leading) { null } + month.days
        cells.chunked(weekdays.size).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, today, onOpenDay)
                    }
                }
                repeat(weekdays.size - week.size) { Box(modifier = Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
private fun DayCell(day: HistoryDay, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val future = day.date.isAfter(today)
    val isToday = day.date == today

    val description = when {
        future -> ""
        day.isEmpty -> "${day.date.dayOfMonth}, nothing logged"
        else -> "${day.date.dayOfMonth}, ${day.elementsTouched} of five, ${Format.spokenDuration(day.logged)} logged"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (future) Modifier else Modifier.clickable { onOpenDay(day.date) })
            .clearAndSetSemantics { if (description.isNotEmpty()) contentDescription = description }
            .padding(vertical = 4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .aspectRatio(1f)
        ) {
            val radius = size.minDimension / 2
            val hairline = 1.dp.toPx()
            when {
                future -> drawCircle(ink.copy(alpha = 0.06f), radius, style = Stroke(hairline))
                day.elementsTouched == 0 && day.isEmpty -> drawCircle(ink.copy(alpha = 0.14f), radius, style = Stroke(hairline))
                day.elementsTouched == 0 -> drawCircle(ink.copy(alpha = 0.14f), radius)
                // Lighter than the small grid's dots: at this size the same alphas read as blobs.
                else -> drawCircle(ink.copy(alpha = 0.13f + 0.13f * day.elementsTouched), radius)
            }
            if (isToday) {
                drawCircle(ink.copy(alpha = 0.8f), radius + hairline * 2.5f, Offset(size.width / 2, size.height / 2), style = Stroke(hairline))
            }
        }
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (future) muted.copy(alpha = 0.4f) else muted,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private const val SLIDE_MILLIS = 280
private const val FADE_MILLIS = 160

/** The week in the order this phone's locale shows it: Monday first, or Sunday. */
private fun weekdayOrder(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0..6).map { first.plus(it.toLong()) }
}
