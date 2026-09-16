package com.sumi.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.sumi.app.ui.SumiFonts
import com.sumi.app.ui.setup.SheetsSection
import com.sumi.app.ui.setup.SheetsViewModel

/**
 * The mark at the top left of the app, and what it opens.
 *
 * Settings about Sumi itself live behind the gear on the right. This side is
 * yours: the Google account your log is copied to, and whatever else comes to
 * belong to you rather than to the app.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("You", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Where your log goes beyond this phone. Everything about Sumi itself is behind the gear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            SheetsSection()
        }
    }
}
