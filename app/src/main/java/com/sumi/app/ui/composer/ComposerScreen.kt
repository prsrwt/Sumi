package com.sumi.app.ui.composer

import android.animation.ValueAnimator
import android.app.TimePickerDialog
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
    fun close() {
        closing = true
        shown.targetState = false
    }

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

    // Finish the activity only after the exit animation has run.
    LaunchedEffect(closing, shown.isIdle, shown.currentState) {
        if (closing && shown.isIdle && !shown.currentState) onClose()
    }

    val noRipple = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tapping the dimmed area outside the sheet dismisses it without logging.
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(ENTER_MILLIS)),
            exit = fadeOut(tween(EXIT_MILLIS))
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
            exit = slideOutVertically(tween(EXIT_MILLIS)) { it / 3 } + fadeOut(tween(EXIT_MILLIS))
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
                    onStartTime = viewModel::setStartTime,
                    onEndTime = viewModel::setEndTime,
                    onSend = viewModel::send,
                    onElement = viewModel::commitWith,
                    onDelete = viewModel::delete
                )
            }
        }
    }
}

private const val ENTER_MILLIS = 260
private const val EXIT_MILLIS = 200

/** Long enough to see the bloom, short enough that logging still feels instant. */
private const val SAVE_HOLD_MILLIS = 240L

@Composable
private fun ComposerCard(
    state: ComposerState,
    onTextChange: (String) -> Unit,
    onStartTime: (java.time.LocalTime) -> Unit,
    onEndTime: (java.time.LocalTime) -> Unit,
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
            onStartTime = onStartTime,
            onEndTime = onEndTime
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
    onStartTime: (java.time.LocalTime) -> Unit,
    onEndTime: (java.time.LocalTime) -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()

    fun pick(instant: Instant, onPicked: (java.time.LocalTime) -> Unit) {
        val local = instant.atZone(zone).toLocalTime()
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(java.time.LocalTime.of(hour, minute)) },
            local.hour,
            local.minute,
            DateFormat.is24HourFormat(context)
        ).show()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { pick(start, onStartTime) }) {
            Text(Format.time(context, start), style = MaterialTheme.typography.titleSmall)
        }
        Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { pick(end, onEndTime) }) {
            Text(Format.time(context, end), style = MaterialTheme.typography.titleSmall)
        }
        Text(
            text = Format.duration(java.time.Duration.between(start, end)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
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
