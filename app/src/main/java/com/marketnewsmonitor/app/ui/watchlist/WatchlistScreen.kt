package com.marketnewsmonitor.app.ui.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.finnhub.TickerSuggestion

@Composable
fun WatchlistScreen(onTickerClick: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val viewModel: WatchlistViewModel = viewModel(factory = WatchlistViewModel.Factory)
    val tickers by viewModel.tickers.collectAsState()
    val tickerSuggestions by viewModel.tickerSuggestions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add ticker")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onQueryChange(it)
                },
                label = { Text("Search watchlist") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true,
            )

            if (tickers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No tickers yet — tap + to add one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tickers, key = { it.symbol }) { ticker ->
                        WatchlistRow(
                            ticker = ticker,
                            onClick = { onTickerClick(ticker.symbol) },
                            onRemove = { viewModel.removeTicker(ticker) },
                            onToggleMuted = { viewModel.toggleMuted(ticker) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTickerDialog(
            suggestions = tickerSuggestions,
            onSymbolQueryChange = viewModel::onAddTickerSymbolChange,
            onDismiss = {
                showAddDialog = false
                viewModel.clearTickerSuggestions()
            },
            onConfirm = { symbol, companyName ->
                viewModel.addTicker(symbol, companyName)
                viewModel.clearTickerSuggestions()
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun WatchlistRow(ticker: Ticker, onClick: () -> Unit, onRemove: () -> Unit, onToggleMuted: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(ticker.symbol, style = MaterialTheme.typography.titleMedium)
                ticker.companyName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Row {
                IconButton(onClick = onToggleMuted) {
                    // No "NotificationsOff" icon in material-icons-core (only in the
                    // much larger -extended artifact) — differentiate by tint instead.
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = if (ticker.muted) "Unmute ${ticker.symbol}" else "Mute ${ticker.symbol}",
                        tint = if (ticker.muted) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${ticker.symbol}")
                }
            }
        }
    }
}

@Composable
private fun AddTickerDialog(
    suggestions: List<TickerSuggestion>,
    onSymbolQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, companyName: String?) -> Unit,
) {
    var symbol by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    // Suppresses the dropdown right after a suggestion is picked, so setting
    // the fields from it doesn't immediately re-trigger a search+reopen.
    var suppressSuggestions by remember { mutableStateOf(false) }

    fun pickSuggestion(suggestion: TickerSuggestion) {
        symbol = suggestion.symbol
        companyName = suggestion.name
        suppressSuggestions = true
        onSymbolQueryChange("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = {
                        symbol = it
                        suppressSuggestions = false
                        onSymbolQueryChange(symbol)
                    },
                    label = { Text("Symbol or company name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!suppressSuggestions && suggestions.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pickSuggestion(suggestion) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Column {
                                    Text(suggestion.symbol, style = MaterialTheme.typography.bodyMedium)
                                    Text(suggestion.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("Company name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(symbol, companyName.ifBlank { null }) },
                enabled = symbol.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
