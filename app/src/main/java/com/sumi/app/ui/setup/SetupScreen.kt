package com.sumi.app.ui.setup

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import com.sumi.app.data.Domains
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.sumi.app.ui.ClockDialog
import com.sumi.app.ui.GlassTabs
import com.sumi.app.ui.SumiFonts
import com.sumi.app.ui.guide.GuideActivity
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
            TimeButton(settings.quietStart, "Quiet hours begin", viewModel::setQuietStart)
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeButton(settings.quietEnd, "Quiet hours end", viewModel::setQuietEnd)
        }

        AddWidgetButton()

        ResetSection(
            message = resetMessage,
            onClearLog = viewModel::clearLog,
            onResetGoals = viewModel::resetGoalsAndRhythm,
            onEraseEverything = viewModel::eraseEverything
        )

        AboutSection()
        TextButton(onClick = onShowIntroduction) { Text("Show the introduction again") }

        Text(
            text = "Sumi asks; it never nags. The widget quietly changes its words. " +
                "There are no notifications, and nothing counts a missed hour against you.",
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

/**
 * One of the five: its element, what it is called, and what that holds.
 *
 * No text field and no box. Tapping the row opens the same kind of sheet the rest
 * of the app asks questions with, holding a list of domains rather than an empty
 * line, because an empty line invites an activity ("gym", "guitar") where a domain
 * is needed, and an activity's spoke can never grow. Writing your own is the last
 * choice in that sheet, for a life these words do not fit.
 */
@Composable
internal fun GoalRow(
    goal: Goal,
    goals: List<Goal>,
    name: String,
    onName: (String) -> Unit,
    onElement: (Element) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sheetOpen by rememberSaveable(goal.slot) { mutableStateOf(false) }
    val holds = Domains.common.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.holds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .clickable { sheetOpen = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true }
                    .clearAndSetSemantics {
                        contentDescription = "${goal.element.displayName} element for ${goal.displayName}. Change element"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = goal.element.kanji,
                    color = Color(goal.element.color),
                    fontSize = 26.sp,
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

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = if (name.isBlank()) {
                        "${goal.element.displayName}, nothing chosen yet. Choose what this one is"
                    } else {
                        "${goal.element.displayName}, $name. Change it"
                    }
                }
        ) {
            Text(
                text = name.ifBlank { "Choose what this is" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground
            )
            if (holds != null) {
                Text(
                    text = holds,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }

    // A rule under each of the five, so they read as one list rather than five
    // floating lines. The last one carries none: nothing follows it to divide.
    if (goal.slot < GOAL_COUNT - 1) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            modifier = Modifier.padding(start = 50.dp)
        )
    }

    if (sheetOpen) {
        DomainSheet(
            goal = goal,
            goals = goals,
            current = name,
            onPick = {
                onName(it)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false }
        )
    }
}

/**
 * What one of the five is, asked the way Sumi asks everything else: a sheet from
 * the bottom, a list to tap, and one quiet way out for anybody the list does not
 * fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DomainSheet(
    goal: Goal,
    goals: List<Goal>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var writing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf(current) }
    // Names another goal already holds are left out: two spokes called the same
    // thing would make the pentagon unreadable.
    val taken = goals.filter { it.slot != goal.slot }.map { it.name.trim().lowercase() }.toSet()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.element.kanji,
                    color = Color(goal.element.color),
                    fontSize = 30.sp,
                    fontFamily = SumiFonts.mincho,
                    modifier = Modifier.padding(end = 14.dp)
                )
                Column {
                    Text("What is this one?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = goal.element.affinity,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (writing) {
                Text(
                    text = "Give it a name broad enough to hold many things. A whole part of your life " +
                        "grows over a year; a single activity stays a sliver forever.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(goal.element.displayName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { writing = false }) { Text("Back to the list") }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { onPick(typed.trim()) },
                        enabled = typed.isNotBlank()
                    ) { Text("Use this") }
                }
            } else {
                val offered = Domains.common.filter { it.name.lowercase() !in taken }
                offered.forEachIndexed { index, domain ->
                    val chosen = domain.name.equals(current.trim(), ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ROW_SHAPE)
                            .clickable { onPick(domain.name) }
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(domain.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = domain.holds,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (chosen) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Chosen",
                                tint = Color(goal.element.color)
                            )
                        }
                    }
                    if (index < offered.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
                TextButton(
                    onClick = { writing = true },
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                ) { Text("Write my own") }
            }
        }
    }
}

@Composable
internal fun TimeButton(time: LocalTime, title: String, onPicked: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val formatter = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a")
    var picking by remember { mutableStateOf(false) }

    TextButton(onClick = { picking = true }) {
        Text(formatter.format(time))
    }
    if (picking) {
        ClockDialog(
            title = title,
            initial = time,
            is24Hour = is24,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onPicked(it)
            }
        )
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
    OutlinedButton(
        onClick = { context.startActivity(Intent(context, GuideActivity::class.java)) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Inside Sumi")
    }
    Text(
        text = "A study guide to how this app is built, chapter by chapter, with the real code.",
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

/** Softly rounded taps, matching the sheets and the glass tabs. */
private val ROW_SHAPE = RoundedCornerShape(12.dp)
