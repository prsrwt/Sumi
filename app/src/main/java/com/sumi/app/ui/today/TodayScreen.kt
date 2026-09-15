package com.sumi.app.ui.today

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.Goal
import com.sumi.app.data.Untagged
import com.sumi.app.data.nameFor
import com.sumi.app.ui.Format
import com.sumi.app.ui.SumiFonts
import com.sumi.app.ui.composer.ComposerActivity
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                DayHeader(
                    state = state,
                    onPrevious = viewModel::previousDay,
                    onNext = viewModel::nextDay
                )
            }

            if (!state.goalsNamed) {
                item {
                    Text(
                        text = "Name your five in setup →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenSetup)
                            .padding(vertical = 8.dp)
                    )
                }
            }

            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (state.isToday) "Nothing logged yet today." else "Nothing was logged this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .animateItem()
                            .padding(vertical = 32.dp)
                    )
                }
            }

            // Newest first: opening the app, the thing you just did is at the top.
            items(state.rows.asReversed(), key = ::rowKey) { row ->
                // Rows ease in, out and into place as entries are logged, edited or
                // deleted, instead of the list jumping.
                when (row) {
                    is TimelineRow.Logged -> LoggedRow(
                        row = row,
                        goals = state.goals,
                        onClick = { context.startActivity(ComposerActivity.edit(context, row.entry.id)) },
                        modifier = Modifier.animateItem()
                    )

                    is TimelineRow.Unlogged -> UnloggedRow(
                        row = row,
                        onClick = { context.startActivity(ComposerActivity.backfill(context, row.from, row.to)) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        if (state.isToday) {
            ExtendedFloatingActionButton(
                onClick = { context.startActivity(ComposerActivity.newEntry(context)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                shape = RoundedCornerShape(percent = 50),
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            ) {
                Text("Note this hour")
            }
        }
    }
}

private fun rowKey(row: TimelineRow): String = when (row) {
    is TimelineRow.Logged -> "e${row.entry.id}"
    is TimelineRow.Unlogged -> "g${row.from.toEpochMilli()}"
}

@Composable
private fun DayHeader(state: TodayState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.isToday) "Today" else DAY_FORMAT.format(state.date),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (state.total.isZero) DATE_FORMAT.format(state.date)
                else "${DATE_FORMAT.format(state.date)} · ${Format.duration(state.total)} logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        IconButton(onClick = onNext, enabled = !state.isToday) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun LoggedRow(
    row: TimelineRow.Logged,
    goals: List<Goal>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val element = row.entry.element
    val goalName = goals.nameFor(element)
    val note = row.entry.text
    // A note is the headline when there is one; the goal's name then sits under it,
    // so time tagged Workout always says Workout somewhere on its row.
    val label = note ?: goalName
    val detail = listOfNotNull(
        goalName.takeIf { note != null && element != null },
        Timeline.spill(row)?.let { spillText(context, it) }
    ).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            // One sentence for a screen reader instead of a kanji and loose fragments.
            .clearAndSetSemantics {
                contentDescription = listOf(
                    "${Format.time(context, row.shownStart)} to ${Format.time(context, row.shownEnd)}",
                    label,
                    detail,
                    Format.spokenDuration(Duration.between(row.shownStart, row.shownEnd)),
                    "Edit"
                ).filter { it.isNotEmpty() }.joinToString(". ")
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeColumn(context, row)

        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text(
                text = element?.kanji ?: Untagged.KANJI,
                color = Color(element?.color ?: Untagged.color),
                fontSize = 20.sp,
                fontFamily = SumiFonts.mincho
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = Format.duration(Duration.between(row.shownStart, row.shownEnd)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "from 11:44 PM yesterday", or with a date when it is further than a day away. */
private fun spillText(context: Context, spill: Timeline.Spill): String {
    val zone = ZoneId.systemDefault()
    val (prefix, at) = when (spill) {
        is Timeline.Spill.StartedEarlier -> "from" to spill.at
        is Timeline.Spill.EndsLater -> "until" to spill.at
    }
    val date = at.atZone(zone).toLocalDate()
    val day = Timeline.relativeDay(date, LocalDate.now(zone)) ?: DAY_FORMAT.format(date)
    return "$prefix ${Format.time(context, at)} $day"
}

@Composable
private fun TimeColumn(context: Context, row: TimelineRow.Logged) {
    Column(modifier = Modifier.width(64.dp)) {
        Text(
            text = Format.time(context, row.shownStart),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = Format.time(context, row.shownEnd),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Deliberately quiet: muted text and no colour. An unlogged stretch is a thing
 * you may want to fill in, not a thing you did wrong.
 */
@Composable
private fun UnloggedRow(row: TimelineRow.Unlogged, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Unlogged, ${Format.spokenDuration(Duration.between(row.from, row.to))}. Fill it in"
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(100.dp))
        Text(
            text = "unlogged · ${Format.duration(Duration.between(row.from, row.to))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "+",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
