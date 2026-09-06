package com.marketnewsmonitor.webapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class ProxyStatus(
    val claudeConfigured: Boolean = false,
    val finnhubConfigured: Boolean = false,
    val alphaVantageConfigured: Boolean = false,
    val name: String = "",
    val email: String = "",
)

/**
 * Read-only: API keys live in server/local.properties, not in the browser
 * (see CLAUDE.md's webapp/server decision) -- this screen only shows
 * whether the proxy has each one configured, it can't set them.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var status by remember { mutableStateOf<ProxyStatus?>(null) }
    var proxyUnreachable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        status = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<ProxyStatus>(proxyGet("/proxy/status"))
        } catch (e: Exception) {
            proxyUnreachable = true
            null
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Keys and your profile live in server/local.properties, next to the " +
                "proxy — edit that file and restart the proxy to change them. This " +
                "screen only shows what's currently configured.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        if (proxyUnreachable) {
            Text(
                "Could not reach the local proxy at localhost:8787 — is it running?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            status?.let { s ->
                StatusRow("Claude API key", s.claudeConfigured)
                StatusRow("Finnhub API key", s.finnhubConfigured)
                StatusRow("Alpha Vantage API key", s.alphaVantageConfigured)
                HorizontalDivider()
                Text("Name: ${s.name.ifBlank { "(not set)" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Email: ${s.email.ifBlank { "(not set)" }}", style = MaterialTheme.typography.bodyMedium)
            } ?: Text("Loading…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusRow(title: String, configured: Boolean) {
    Text(
        "$title: ${if (configured) "configured ✓" else "missing ✗"}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}
