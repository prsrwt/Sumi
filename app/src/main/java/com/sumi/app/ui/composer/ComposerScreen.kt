package com.sumi.app.ui.composer

import androidx.activity.compose.BackHandler

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import com.sumi.app.data.Domain
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.sumi.app.data.Word
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.Element
import com.sumi.app.data.Goal
import com.sumi.app.ui.Format
import com.sumi.app.ui.SumiFonts
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
fun ComposerScreen(
    onClose: () -> Unit,
    viewModel: ComposerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The sheet owns its own entrance and exit, so opening and closing each read
    // as one motion rather than a window fade layered over a slide.
    val shown = remember { MutableTransitionState(false) }

    // Set only by an actual request to close. Without it, the moment between
    // loading finishing and the sheet being told to appear also looks like "idle
    // and hidden", and the composer would finish itself the instant it opened.
    var closing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    fun close() {
        if (closing) return
        // The keyboard goes down first, as its own smooth motion, and the sheet
        // sinks with it. Leaving the keyboard up until the window closed made it
        // vanish in a single frame, which read as a stutter.
        focusManager.clearFocus()
        keyboard?.hide()
        closing = true
        shown.targetState = false
    }

    // How much of the screen the keyboard still covers, readable from a coroutine.
    val imeBottom by rememberUpdatedState(WindowInsets.ime.getBottom(LocalDensity.current))

    // Back closes the sheet the same way saving does, rather than snapping the
    // window shut with the keyboard still up.
    BackHandler(enabled = !closing) { close() }

    LaunchedEffect(state.loading) {
        if (!state.loading) shown.targetState = true
    }

    // Once a save lands, hold for a beat so the ink-bloom on the tapped kanji is
    // actually seen, then let the sheet sink away. Skipped entirely when the
    // system has animations switched off - there is nothing to wait for.
    LaunchedEffect(state.done) {
        if (state.done) {
            if (ValueAnimator.areAnimatorsEnabled()) delay(SAVE_HOLD_MILLIS)
            close()
        }
    }

    // Finish the activity only once the sheet has sunk away and the keyboard is
    // down, so nothing disappears abruptly with the window. The wait for the
    // keyboard is capped, for phones whose keyboard never reports closing.
    LaunchedEffect(closing, shown.isIdle, shown.currentState) {
        if (closing && shown.isIdle && !shown.currentState) {
            withTimeoutOrNull(KEYBOARD_WAIT_MILLIS) { snapshotFlow { imeBottom }.first { it == 0 } }
            onClose()
        }
    }

    val noRipple = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tapping the dimmed area outside the sheet dismisses it without logging.
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(ENTER_MILLIS)),
            exit = fadeOut(tween(EXIT_MILLIS, easing = LinearOutSlowInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = noRipple, indication = null) { close() }
            )
        }

        AnimatedVisibility(
            visibleState = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(ENTER_MILLIS)) { it / 3 } + fadeIn(tween(ENTER_MILLIS)),
            // Sinks all the way down, easing into the motion like something let go.
            exit = slideOutVertically(tween(EXIT_MILLIS, easing = FastOutLinearInEasing)) { it } +
                fadeOut(tween(EXIT_MILLIS, delayMillis = EXIT_MILLIS / 3))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(12.dp)
                    // Swallows taps so touching the sheet itself doesn't dismiss it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 12.dp
            ) {
                val saved = state.askedAfterSaving
                if (saved != null) {
                    KeptWherePrompt(
                        saved = saved,
                        domains = state.domains,
                        onChoose = viewModel::keepInto,
                        onSkip = viewModel::keepNothing
                    )
                } else ComposerCard(
                    state = state,
                    onTextChange = viewModel::onTextChange,
                    onRange = viewModel::setRange,
                    onSend = viewModel::send,
                    onElement = viewModel::commitWith,
                    onWord = viewModel::logWord,
                    onDelete = viewModel::delete
                )
            }
        }
    }
}

private const val ENTER_MILLIS = 260
private const val EXIT_MILLIS = 300

/** The longest the composer waits for the keyboard to finish going down. */
private const val KEYBOARD_WAIT_MILLIS = 450L

/** Long enough to see the bloom, short enough that logging still feels instant. */
private const val SAVE_HOLD_MILLIS = 240L

@Composable
private fun ComposerCard(
    state: ComposerState,
    onTextChange: (String) -> Unit,
    onRange: (java.time.LocalTime, java.time.LocalTime) -> Unit,
    onSend: () -> Unit,
    onElement: (Element) -> Unit,
    onWord: (Word) -> Unit,
    onDelete: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = state.question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        RangeRow(
            start = state.start,
            end = state.end,
            onRange = onRange
        )

        TextField(
            value = state.text,
            onValueChange = onTextChange,
            placeholder = { Text("Type what you're doing") },
            // One line on purpose. A multi-line field turns the keyboard's action
            // key into a newline, so Send would never fire from the keyboard -
            // and a log entry is one line of text anyway.
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSend = { if (state.text.isNotBlank()) onSend() }
            ),
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = state.text.isNotBlank() && !state.saving
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Log")
                }
            }
        )

        WordRow(
            state = state,
            onWord = onWord
        )

        ElementRow(
            goals = state.goals,
            selected = state.element,
            enabled = !state.saving,
            onElement = onElement
        )

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.editingId != null) {
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text("Delete entry")
            }
        }
    }
}

/**
 * Your own words, most recently used first, and the offer to keep a new one.
 *
 * Tapping a word is a whole log: it carries its own element, so nothing else has
 * to be chosen. Typing something Sumi has not seen shows one quiet offer to keep
 * it, the way a new board appears when you pin to it; ignoring the offer logs the
 * line as a note and keeps nothing.
 */
@Composable
private fun WordRow(
    state: ComposerState,
    onWord: (Word) -> Unit
) {
    if (state.words.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.words.forEach { word ->
            WordChip(
                label = word.name,
                kanji = word.element.kanji,
                color = Color(word.element.color),
                selected = false,
                enabled = !state.saving,
                description = "Log ${word.name}",
                onClick = { onWord(word) }
            )
        }
    }
}

@Composable
private fun WordChip(
    label: String,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    kanji: String? = null
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) color.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) color.copy(alpha = 0.5f) else ink.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (kanji != null) {
            Text(
                text = kanji,
                color = color,
                fontSize = 15.sp,
                fontFamily = SumiFonts.mincho,
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) ink else ink.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

/**
 * Asked after the entry is saved, never before it: the hour is already logged, and
 * this is only about whether the word is worth keeping.
 *
 * Grouped under each element with the kanji shown once, because a flat list
 * repeats the same mark down the side and reads like five of the same thing.
 */
@Composable
private fun KeptWherePrompt(
    saved: Saved,
    domains: List<Domain>,
    onChoose: (Domain) -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Logged. Keep \u201C${saved.word}\u201D?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Put it with a part of your life and it becomes one tap next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSkip) { Text("No need") }
        }

        Column(
            modifier = Modifier
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Element.entries.forEach { element ->
                val here = domains.filter { it.element == element }
                if (here.isEmpty()) return@forEach
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = element.kanji,
                        color = Color(element.color),
                        fontFamily = SumiFonts.mincho,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    // The element's own name, not the goal's: the goal's name is
                    // the first row underneath, and saying it twice reads as a bug.
                    Text(
                        text = element.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                here.forEach { domain ->
                    Text(
                        text = domain.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onChoose(domain) }
                            .padding(vertical = 9.dp, horizontal = 26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeRow(
    start: Instant,
    end: Instant,
    onRange: (java.time.LocalTime, java.time.LocalTime) -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var pickerSide by remember { mutableStateOf<RangeSide?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { pickerSide = RangeSide.FROM }) {
            Text(Format.time(context, start), style = MaterialTheme.typography.titleSmall)
        }
        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { pickerSide = RangeSide.TO }) {
            Text(Format.time(context, end), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            text = Format.duration(java.time.Duration.between(start, end)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }

    pickerSide?.let { side ->
        RangePickerDialog(
            start = start.atZone(zone).toLocalTime(),
            end = end.atZone(zone).toLocalTime(),
            initialSide = side,
            is24Hour = DateFormat.is24HourFormat(context),
            onDismiss = { pickerSide = null },
            onConfirm = { from, to ->
                pickerSide = null
                onRange(from, to)
            }
        )
    }
}

/**
 * The five elements as one-tap logs. The goal name sits under each glyph so the
 * mapping can be learned; after a week the kanji alone are enough.
 */
@Composable
private fun ElementRow(
    goals: List<Goal>,
    selected: Element?,
    enabled: Boolean,
    onElement: (Element) -> Unit
) {
    val view = LocalView.current
    var bloomed by remember { mutableStateOf<Element?>(null) }
    val bloom = remember { Animatable(0f) }

    LaunchedEffect(bloomed) {
        if (bloomed != null) {
            bloom.snapTo(0f)
            bloom.animateTo(1f, tween(BLOOM_MILLIS))
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        goals.sortedBy { it.slot }.forEach { goal ->
            val isSelected = goal.element == selected
            val inkColor = Color(goal.element.color)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = enabled) {
                        // A tick you feel, so the log lands even without looking.
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                            else HapticFeedbackConstants.KEYBOARD_TAP
                        )
                        bloomed = goal.element
                        onElement(goal.element)
                    }
                    .clearAndSetSemantics {
                        contentDescription = "Log to ${goal.displayName}"
                        role = Role.Button
                        if (isSelected) stateDescription = "Selected"
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        // Ink spreading into paper: the tapped glyph's colour blooms
                        // outward and fades. Drawn before the clip so it can spread
                        // past the glyph's own circle.
                        .drawBehind {
                            if (bloomed == goal.element) {
                                val p = bloom.value
                                drawCircle(
                                    color = inkColor.copy(alpha = 0.32f * (1f - p)),
                                    radius = size.minDimension / 2 * (0.7f + 0.9f * p)
                                )
                            }
                        }
                        .clip(CircleShape)
                        .background(if (isSelected) inkColor.copy(alpha = 0.18f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = goal.element.kanji,
                        color = inkColor,
                        fontSize = 26.sp,
                        fontFamily = SumiFonts.mincho
                    )
                }
                Text(
                    text = goal.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Two lines, because a fifth of the width is narrow and names
                    // like "Home and care" would otherwise be cut to "Home an".
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private const val BLOOM_MILLIS = 380
