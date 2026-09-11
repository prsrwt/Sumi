package com.beaver.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beaver.app.BuildConfig
import com.beaver.app.data.DayProgress
import com.beaver.app.data.GOAL_COUNT
import com.beaver.app.data.Goal
import java.time.LocalDate

@Composable
fun GoalSetupScreen(
    modifier: Modifier = Modifier,
    viewModel: GoalSetupViewModel = viewModel()
) {
    val names by viewModel.names.collectAsStateWithLifecycle()
    val previewGoals by viewModel.previewGoals.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = names) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> GoalList(
                names = current,
                previewGoals = previewGoals,
                history = history,
                onNameChanged = viewModel::onNameChanged,
                onFillSample = viewModel::fillSampleHistory,
                onClearHistory = viewModel::clearHistory
            )
        }
    }
}

@Composable
private fun GoalList(
    names: List<String>,
    previewGoals: List<Goal>,
    history: List<DayProgress>,
    onNameChanged: (Int, String) -> Unit,
    onFillSample: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Your five things",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Five goals, every day. Tick them off on the widget.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WidgetFacePreview(
            goals = previewGoals,
            history = history,
            today = LocalDate.now()
        )

        if (BuildConfig.DEBUG) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onFillSample, modifier = Modifier.weight(1f)) {
                    Text("Sample data")
                }
                OutlinedButton(onClick = onClearHistory, modifier = Modifier.weight(1f)) {
                    Text("Clear")
                }
            }
        }

        repeat(GOAL_COUNT) { slot ->
            OutlinedTextField(
                value = names.getOrElse(slot) { "" },
                onValueChange = { onNameChanged(slot, it) },
                label = { Text("Goal ${slot + 1}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (slot == GOAL_COUNT - 1) ImeAction.Done else ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
