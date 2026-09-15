package com.sumi.app.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sumi.app.data.TimeRange
import com.sumi.app.ui.Format
import com.sumi.app.ui.GlassTabs
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class RangeSide { FROM, TO }

/**
 * Both ends of an entry in one dialog.
 *
 * Separate pickers for start and end made it easy to set the start and never
 * look at the end. Here the two sit behind a From | To switch: setting From
 * offers Next, which moves straight to To, and To offers Back and Done. Either
 * side can be reached at any time from the switch, and the length of the whole
 * range updates live, so it is always visible what will be saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(
    start: LocalTime,
    end: LocalTime,
    initialSide: RangeSide,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (from: LocalTime, to: LocalTime) -> Unit
) {
    var side by remember { mutableStateOf(initialSide) }
    val fromState = rememberTimePickerState(start.hour, start.minute, is24Hour)
    val toState = rememberTimePickerState(end.hour, end.minute, is24Hour)

    val from = LocalTime.of(fromState.hour, fromState.minute)
    val to = LocalTime.of(toState.hour, toState.minute)
    val format = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    val overnight = !to.isAfter(from)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassTabs(
                    tabs = listOf("From  ${format.format(from)}", "To  ${format.format(to)}"),
                    selectedIndex = side.ordinal,
                    onSelect = { side = RangeSide.entries[it] },
                    modifier = Modifier.fillMaxWidth()
                )

                // Keyed by side so switching resets the dial's hour/minute mode
                // while each side's chosen time lives on in its own state.
                key(side) {
                    TimePicker(state = if (side == RangeSide.FROM) fromState else toState)
                }

                Text(
                    text = Format.duration(TimeRange.length(from, to)) + if (overnight) " · overnight" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    when (side) {
                        RangeSide.FROM -> {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            TextButton(onClick = { side = RangeSide.TO }) {
                                Text("Next: end time", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        RangeSide.TO -> {
                            TextButton(onClick = { side = RangeSide.FROM }) { Text("Back") }
                            TextButton(onClick = { onConfirm(from, to) }) {
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
