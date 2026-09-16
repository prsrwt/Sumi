package com.sumi.app.ui.balance

import androidx.compose.foundation.Canvas
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
 * The whole log as month calendars you can scroll back through. Each day is the
 * same dot as the 30-day grid: darker the more of your five got time. Tapping one
 * opens that day on Today.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    onOpenDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    val months by viewModel.months.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val weekdays = weekdayOrder(locale)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
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

/** The week in the order this phone's locale shows it: Monday first, or Sunday. */
private fun weekdayOrder(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0..6).map { first.plus(it.toLong()) }
}
