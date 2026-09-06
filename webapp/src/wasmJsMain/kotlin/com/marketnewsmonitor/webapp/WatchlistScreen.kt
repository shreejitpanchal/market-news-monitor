package com.marketnewsmonitor.webapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun WatchlistScreen(onTickerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val tickers by WatchlistStore.tickers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) { Text("+") }
        },
    ) { padding ->
        if (tickers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tickers yet — tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tickers, key = { it.symbol }) { ticker ->
                    Card(modifier = Modifier.fillMaxWidth(), onClick = { onTickerClick(ticker.symbol) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(ticker.symbol, style = MaterialTheme.typography.titleMedium)
                                ticker.companyName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            IconButton(onClick = { WatchlistStore.remove(ticker.symbol) }) { Text("✕") }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTickerDialog(onDismiss = { showAddDialog = false })
    }
}

/** Real symbol search via the proxy (FinnhubClient), debounced 300ms -- same approach as the Android app's Add Ticker dialog. */
@Composable
private fun AddTickerDialog(onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<TickerSuggestion>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = FinnhubClient.search(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Symbol or company name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                WatchlistStore.add(suggestion.symbol, suggestion.name)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Column {
                            Text(suggestion.symbol, style = MaterialTheme.typography.bodyMedium)
                            Text(suggestion.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (query.isNotBlank()) WatchlistStore.add(query, null)
                    onDismiss()
                },
                enabled = query.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
