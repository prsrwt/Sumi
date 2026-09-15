package com.sumi.app.ui.setup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumi.app.sync.Timesheet
import com.sumi.app.ui.Format
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Connecting, and what is connected. Nothing here nags: a lost connection is one
 * quiet line and a Reconnect button, never a notification.
 */
@Composable
fun SheetsSection(viewModel: SheetsViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.resultCode, result.data) }

    LaunchedEffect(consent) {
        val pending = consent ?: return@LaunchedEffect
        consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
        viewModel.consentLaunched()
    }

    SectionTitle("Google Sheets")
    if (!ui.loaded) return

    val link = ui.link
    when {
        link == null -> {
            Body("Keep a copy of your log in a spreadsheet in your own Google Drive. Sumi can only see the one sheet it makes.")
            OutlinedButton(
                onClick = viewModel::connect,
                enabled = !ui.working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ui.working) "Connecting…" else "Connect Google Sheets")
            }
        }

        link.needsReconnect -> {
            Body("Google access for ${link.accountEmail} has ended, so Sumi Timesheet isn't being updated.")
            OutlinedButton(
                onClick = viewModel::connect,
                enabled = !ui.working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ui.working) "Connecting…" else "Reconnect")
            }
            TextButton(onClick = viewModel::disconnect) { Text("Disconnect") }
        }

        else -> {
            Text("Connected as ${link.accountEmail}", style = MaterialTheme.typography.bodyLarge)
            Body(
                when {
                    ui.waiting > 0 -> "Changes waiting to go to Sumi Timesheet. They'll send when you're online."
                    link.lastSyncedAt != null ->
                        "Sumi Timesheet is up to date · last synced ${Format.time(context, link.lastSyncedAt)}" +
                            if (link.lastSyncedAt.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()) ""
                            else ", ${DAY.format(link.lastSyncedAt.atZone(ZoneId.systemDefault()))}"
                    else -> "Sumi Timesheet is up to date."
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = {
                    val open = Intent(Intent.ACTION_VIEW, Uri.parse(Timesheet.url(link.spreadsheetId)))
                    try {
                        context.startActivity(open)
                    } catch (e: ActivityNotFoundException) {
                        // No browser or Sheets app; nothing sensible to open it with.
                    }
                }) {
                    Text("Open sheet")
                }
                TextButton(onClick = viewModel::disconnect) { Text("Disconnect") }
            }
        }
    }

    ui.message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
