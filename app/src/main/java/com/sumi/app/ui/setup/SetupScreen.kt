package com.sumi.app.ui.setup

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.Element
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.data.Goal
import com.sumi.app.ui.GlassTabs
import com.sumi.app.widget.SumiWidgetReceiver
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel()
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Setup", style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle("Your five")
        Text(
            text = "The five things you most want your time to go to. Each gets an element; " +
                "tap an element in the composer and that time is logged to it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val currentNames = names
        if (currentNames != null && goals.size == GOAL_COUNT) {
            goals.sortedBy { it.slot }.forEach { goal ->
                GoalRow(
                    goal = goal,
                    name = currentNames.getOrElse(goal.slot) { "" },
                    onName = { viewModel.onNameChanged(goal.slot, it) },
                    onElement = { viewModel.assign(goal.slot, it) }
                )
            }
        }

        SectionTitle("Rhythm")
        Text(
            text = "How long after your last entry the widget asks again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val intervals = listOf(30L, 45L, 60L, 90L)
        GlassTabs(
            tabs = intervals.map { "$it min" },
            selectedIndex = intervals.indexOf(settings.askInterval.toMinutes()).coerceAtLeast(0),
            onSelect = { viewModel.setInterval(intervals[it]) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Quiet hours",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            TimeButton(settings.quietStart, viewModel::setQuietStart)
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeButton(settings.quietEnd, viewModel::setQuietEnd)
        }

        AddWidgetButton()

        Text(
            text = "Sumi asks; it never nags. The widget quietly changes its words — " +
                "there are no notifications, and nothing counts a missed hour against you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun GoalRow(
    goal: Goal,
    name: String,
    onName: (String) -> Unit,
    onElement: (Element) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = goal.element.kanji,
                    color = Color(goal.element.color),
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Element.entries.forEach { element ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${element.kanji}  ${element.displayName}")
                                Text(
                                    text = element.affinity,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            menuOpen = false
                            onElement(element)
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = onName,
            placeholder = { Text(goal.element.displayName) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}

@Composable
private fun TimeButton(time: LocalTime, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val formatter = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a")

    TextButton(onClick = {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
            time.hour,
            time.minute,
            is24
        ).show()
    }) {
        Text(formatter.format(time))
    }
}

/**
 * Asks the launcher to place the widget, which beats explaining the long-press,
 * find-it-in-the-list, drag-it-out dance. Hidden where the launcher can't pin.
 */
@Composable
private fun AddWidgetButton() {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(AppWidgetManager::class.java) }
    if (manager == null || !manager.isRequestPinAppWidgetSupported) return

    OutlinedButton(
        onClick = {
            manager.requestPinAppWidget(ComponentName(context, SumiWidgetReceiver::class.java), null, null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text("Add Sumi to your home screen")
    }
}
