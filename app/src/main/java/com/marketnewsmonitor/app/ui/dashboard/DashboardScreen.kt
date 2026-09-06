package com.marketnewsmonitor.app.ui.dashboard

import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marketnewsmonitor.app.data.local.entity.Ticker

@Composable
fun DashboardScreen(onTickerClick: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
    val tickers by viewModel.tickers.collectAsState()

    if (tickers.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Add tickers on the Watchlist tab to see them here.")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tickers, key = { it.symbol }) { ticker ->
            TickerCard(ticker, onClick = { onTickerClick(ticker.symbol) })
        }
    }
}

@Composable
private fun TickerCard(ticker: Ticker, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(ticker.symbol, style = MaterialTheme.typography.titleMedium)
            ticker.companyName?.let { Text(it) }
            Text("Tap for the latest news.")
        }
    }
}
