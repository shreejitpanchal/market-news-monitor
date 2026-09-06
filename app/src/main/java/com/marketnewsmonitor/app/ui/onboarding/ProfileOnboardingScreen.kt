package com.marketnewsmonitor.app.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProfileOnboardingScreen(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ProfileOnboardingViewModel = viewModel(factory = ProfileOnboardingViewModel.Factory)
    val importStatus by viewModel.importStatus.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { pendingImportUri = it } }

    val emailValid = isValidProfileEmail(email)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to MarketNewsMonitor", style = MaterialTheme.typography.headlineSmall)
        Text(
            "One-time setup, required before you can use the app. SEC EDGAR's " +
                "fair-access policy requires every filings request be traceable to a " +
                "real requester — your name and email are sent in plain text to SEC's " +
                "servers with every filings request. That's the only place they go; no " +
                "other data source or provider in this app receives them.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            isError = email.isNotBlank() && !emailValid,
            modifier = Modifier.fillMaxWidth(),
        )
        if (email.isNotBlank() && !emailValid) {
            Text(
                "Enter a valid email address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = {
                viewModel.saveProfile(name, email)
                onComplete()
            },
            enabled = name.isNotBlank() && emailValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Get started") }

        HorizontalDivider()

        Text(
            "Restoring an existing setup? Importing fills these in for you.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import setup instead") }

        if (importStatus is OnboardingImportStatus.Importing) {
            Text("Importing…", style = MaterialTheme.typography.bodySmall)
        }
        (importStatus as? OnboardingImportStatus.Error)?.let {
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    pendingImportUri?.let { uri ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Backup password") },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importSetup(uri, password, onComplete)
                        pendingImportUri = null
                    },
                    enabled = password.isNotBlank(),
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") } },
        )
    }
}
