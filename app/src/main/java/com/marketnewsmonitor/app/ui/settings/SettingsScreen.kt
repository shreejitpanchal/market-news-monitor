package com.marketnewsmonitor.app.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marketnewsmonitor.app.data.settings.AppPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val claudeApiKey by viewModel.claudeApiKey.collectAsState()
    val finnhubApiKey by viewModel.finnhubApiKey.collectAsState()
    val pollingEnabled by viewModel.pollingEnabled.collectAsState()
    val pollIntervalMinutes by viewModel.pollIntervalMinutes.collectAsState()
    val status by viewModel.status.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var notificationDenied by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationDenied = !granted
        viewModel.setPollingEnabled(true)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportSetup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pendingImportUri = it } }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ApiKeyField(
            title = "Claude API key",
            storedValue = claudeApiKey,
            onSave = viewModel::saveClaudeApiKey,
        )

        ApiKeyField(
            title = "Finnhub API key",
            storedValue = finnhubApiKey,
            onSave = viewModel::saveFinnhubApiKey,
            helperText = "Free tier at finnhub.io — needed for company news on the ticker detail screen.",
        )

        HorizontalDivider()

        Text("Background polling", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
                checked = pollingEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setPollingEnabled(enabled)
                    }
                },
            )
            Text(if (pollingEnabled) "On" else "Off")
        }
        if (notificationDenied) {
            Text(
                "Notification permission denied — polling still runs, but you won't see alerts until it's granted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPreferences.ALLOWED_POLL_INTERVALS_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = pollIntervalMinutes == minutes,
                    onClick = { viewModel.setPollIntervalMinutes(minutes) },
                    label = { Text("${minutes}m") },
                )
            }
        }

        HorizontalDivider()

        Text("Export / import setup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exports your watchlist and API keys to a file you choose. " +
                "This file contains your API keys in plain text — store it securely, " +
                "the same way you would a password.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedButton(
            onClick = {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                exportLauncher.launch("marketnewsmonitor-backup-$stamp.json")
            },
        ) { Text("Export setup") }

        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
            Text("Import setup")
        }

        when (val current = status) {
            is BackupStatus.Success -> Text(current.message, color = MaterialTheme.colorScheme.primary)
            is BackupStatus.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
            BackupStatus.Exporting -> Text("Exporting…")
            BackupStatus.Importing -> Text("Importing…")
            BackupStatus.Idle -> {}
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import setup?") },
            text = { Text("This replaces your current watchlist and API keys with the contents of the chosen file.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importSetup(uri)
                    pendingImportUri = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ApiKeyField(
    title: String,
    storedValue: String,
    onSave: (String) -> Unit,
    helperText: String? = null,
) {
    var input by remember(storedValue) { mutableStateOf(storedValue) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        helperText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(input) }, enabled = input != storedValue) {
            Text("Save key")
        }
    }
}
