package com.marketnewsmonitor.webapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Local UI state only — nothing here is persisted (no localStorage, no
 * EncryptedSharedPreferences equivalent) and nothing is sent anywhere. A
 * page refresh resets these fields. This is a visual preview of the
 * Settings screen's layout, not a working settings store.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var claudeKey by remember { mutableStateOf("") }
    var finnhubKey by remember { mutableStateOf("") }
    var alphaVantageKey by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Preview only — nothing on this screen is saved or sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        ApiKeyField("Claude API key", claudeKey) { claudeKey = it }
        ApiKeyField("Finnhub API key", finnhubKey) { finnhubKey = it }
        ApiKeyField("Alpha Vantage API key", alphaVantageKey) { alphaVantageKey = it }
    }
}

@Composable
private fun ApiKeyField(title: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
