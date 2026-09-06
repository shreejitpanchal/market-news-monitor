package com.marketnewsmonitor.webapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Urgency badges aren't shown here — computing them would mean classifying
 * every watchlisted ticker's news on every dashboard load, and this client
 * is manual-refresh-only (no background polling). Open a ticker and hit
 * Refresh to see its classified articles.
 */
@Composable
fun DashboardScreen(onTickerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val tickers by WatchlistStore.tickers.collectAsState()

    if (tickers.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tickers yet — add one from the Watchlist tab.")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tickers, key = { it.symbol }) { ticker ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onTickerClick(ticker.symbol) }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(ticker.symbol, style = MaterialTheme.typography.titleMedium)
                    ticker.companyName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
