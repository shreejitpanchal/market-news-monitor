package com.marketnewsmonitor.webapp

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Add ticker" here only appends to the in-memory sample list for the
 * current page load — this is a UI preview, nothing persists across a
 * refresh and nothing is fetched from a real symbol-search API.
 */
@Composable
fun WatchlistScreen(onTickerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    var tickers by remember { mutableStateOf(SampleData.tickers) }
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
                        ) {
                            Column {
                                Text(ticker.symbol, style = MaterialTheme.typography.titleMedium)
                                Text(ticker.companyName, style = MaterialTheme.typography.bodySmall)
                            }
                            UrgencyBadge(ticker.urgency)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var symbol by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add ticker (preview only)") },
            text = {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("Symbol") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (symbol.isNotBlank()) {
                            tickers = tickers + SampleTicker(symbol.uppercase(), "Sample company", Urgency.CALM)
                        }
                        showAddDialog = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
        )
    }
}
