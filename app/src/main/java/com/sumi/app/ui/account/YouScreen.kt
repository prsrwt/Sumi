package com.sumi.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.data.GOAL_COUNT
import com.sumi.app.ui.SumiFonts
import com.sumi.app.ui.setup.SectionTitle
import com.sumi.app.ui.setup.SetupViewModel
import com.sumi.app.ui.setup.SheetsSection
import com.sumi.app.ui.setup.SheetsViewModel

/**
 * The mark at the top left of the app, and the screen it opens.
 *
 * Settings about Sumi itself live behind the gear on the right. This side is
 * yours: the five things your time is sorted into, and the Google account your
 * log is copied to.
 */
@Composable
fun AccountMark(onClick: () -> Unit, viewModel: SheetsViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ink = MaterialTheme.colorScheme.onBackground
    val email = ui.link?.accountEmail

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.06f))
            .border(1.dp, ink.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = if (email == null) "You. Nothing connected yet" else "You, connected as $email"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            // The account's initial once connected; the ink mark until then.
            text = email?.firstOrNull()?.uppercase() ?: "墨",
            fontFamily = SumiFonts.mincho,
            fontSize = if (email == null) 20.sp else 17.sp,
            color = ink
        )
    }
}

@Composable
fun YouScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel()
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val names by viewModel.names.collectAsStateWithLifecycle()

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
            Text("You", style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle("Your five")
        Text(
            text = "The five things your time is sorted into. Turn the wheel to the life closest to " +
                "yours, or write your own five below. Each keeps its element, and tapping that " +
                "element in the composer logs the time to it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val currentNames = names
        if (currentNames != null && goals.size == GOAL_COUNT) {
            // Turning the wheel changes nothing. Pressing its button does, and it
            // asks first if there is anything to lose.
            FiveChooser(setup = viewModel, modifier = Modifier.fillMaxWidth())

            Text(
                text = "Most weeks lean. Yours will too: the shape above is an example, not something to match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            goals.sortedBy { it.slot }.forEach { goal ->
                ElementRow(
                    goal = goal,
                    goals = goals,
                    onElement = { viewModel.assign(goal.slot, it) }
                )
            }
        }

        SheetsSection()
    }

}
