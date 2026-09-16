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
                ComposerCard(
                    state = state,
                    onTextChange = viewModel::onTextChange,
                    onRange = viewModel::setRange,
                    onSend = viewModel::send,
                    onElement = viewModel::commitWith,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private const val BLOOM_MILLIS = 380
