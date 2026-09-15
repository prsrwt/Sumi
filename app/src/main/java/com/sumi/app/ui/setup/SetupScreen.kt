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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.BuildConfig
import com.sumi.app.data.Element
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.data.Goal
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiFonts
import com.sumi.app.widget.SumiWidgetReceiver
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SetupScreen(
    onBack: () -> Unit,
    onShowIntroduction: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel()
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val resetMessage by viewModel.resetMessage.collectAsStateWithLifecycle()

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
                    goals = goals,
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

        SheetsSection()

        ResetSection(
            message = resetMessage,
            onClearLog = viewModel::clearLog,
            onResetGoals = viewModel::resetGoalsAndRhythm,
            onEraseEverything = viewModel::eraseEverything
        )

        AboutSection()
        TextButton(onClick = onShowIntroduction) { Text("Show the introduction again") }

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
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
internal fun GoalRow(
    goal: Goal,
    goals: List<Goal>,
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
                    fontFamily = SumiFonts.mincho
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Element.entries.forEach { element ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${element.kanji}  ${element.displayName}", fontFamily = SumiFonts.mincho)
                                // Choosing an element another goal holds swaps the two,
                                // so say whose it is by the name the user gave it.
                                val holder = goals.firstOrNull { it.element == element && it.slot != goal.slot }
                                Text(
                                    text = if (holder != null && holder.name.isNotBlank()) {
                                        "${holder.name} · swaps with this goal"
                                    } else {
                                        element.affinity
                                    },
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
internal fun TimeButton(time: LocalTime, onPicked: (LocalTime) -> Unit) {
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
internal fun AddWidgetButton() {
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

private enum class ResetKind(
    val button: String,
    val title: String,
    val body: String,
    val confirm: String
) {
    LOG(
        button = "Clear time log",
        title = "Clear your time log?",
        body = "Every entry on this phone is deleted. Your five goals and your rhythm settings stay as they are. " +
            "If Google Sheets is connected, its month tabs are emptied too.",
        confirm = "Clear log"
    ),
    GOALS(
        button = "Reset goals and rhythm",
        title = "Reset goals and rhythm?",
        body = "Your five names are cleared, the elements return to their original order, and the rhythm " +
            "goes back to every 45 minutes with quiet hours from 23:00 to 07:00. Your time log stays.",
        confirm = "Reset"
    ),
    EVERYTHING(
        button = "Erase everything",
        title = "Erase everything?",
        body = "Your time log, your five goals and your settings are all deleted, and Sumi goes back to how " +
            "it was when you installed it. Google Sheets is disconnected; the spreadsheet itself stays in your Drive.",
        confirm = "Erase everything"
    )
}

/**
 * Three separate resets rather than one, so clearing a messy week of logs never
 * costs the goals you set up, and each is confirmed with exactly what it removes.
 *
 * No red, deliberately: the app avoids red everywhere. The dialogs rely on plain
 * wording and a named confirm button instead of alarm colour.
 */
@Composable
private fun ResetSection(
    message: String?,
    onClearLog: () -> Unit,
    onResetGoals: () -> Unit,
    onEraseEverything: () -> Unit
) {
    var pending by remember { mutableStateOf<ResetKind?>(null) }

    SectionTitle("Reset")
    Text(
        text = "Deleted data can't be recovered.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ResetKind.entries.forEach { kind ->
        OutlinedButton(onClick = { pending = kind }, modifier = Modifier.fillMaxWidth()) {
            Text(kind.button)
        }
    }

    message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    pending?.let { kind ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(kind.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(kind.body)
                    Text("This can't be undone.", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (kind) {
                        ResetKind.LOG -> onClearLog()
                        ResetKind.GOALS -> onResetGoals()
                        ResetKind.EVERYTHING -> onEraseEverything()
                    }
                }) {
                    Text(kind.confirm, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * The version, and the typeface credit. The SIL Open Font License requires its
 * text to travel with the font, so the full licence is one tap away rather than
 * only sitting unread inside the APK.
 */
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    var showLicence by remember { mutableStateOf(false) }

    SectionTitle("About")
    Text(
        text = "Sumi ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Set in Shippori Mincho, © The Shippori Mincho Project Authors, " +
            "under the SIL Open Font License 1.1.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(onClick = { showLicence = true }) { Text("Read the font licence") }

    if (showLicence) {
        val licence = remember {
            runCatching {
                context.assets.open("licenses/shippori_mincho_OFL.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("The licence text could not be loaded.")
        }
        AlertDialog(
            onDismissRequest = { showLicence = false },
            title = { Text("SIL Open Font License 1.1") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(licence, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showLicence = false }) { Text("Close") } }
        )
    }
}
