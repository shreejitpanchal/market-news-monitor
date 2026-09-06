package com.marketnewsmonitor.webapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed interface RefreshState {
    data object Idle : RefreshState
    data object Refreshing : RefreshState
    data class Done(val message: String) : RefreshState
}

/**
 * Manual refresh only -- no background polling in the browser (see
 * CLAUDE.md's webapp/server decision). Fetches Finnhub news + SEC EDGAR
 * filings, classifies them with Claude, and fetches a price chart, all
 * through the local proxy.
 */
@Composable
fun TickerDetailScreen(symbol: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tickers by WatchlistStore.tickers.collectAsState()
    val ticker = tickers.firstOrNull { it.symbol == symbol }
    var articles by remember(symbol) { mutableStateOf<List<Article>>(emptyList()) }
    var priceHistory by remember(symbol) { mutableStateOf<List<PricePoint>>(emptyList()) }
    var refreshState by remember(symbol) { mutableStateOf<RefreshState>(RefreshState.Idle) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        refreshState = RefreshState.Refreshing
        scope.launch {
            val news = FinnhubClient.companyNews(symbol)
            val filings = EdgarClient.filings(symbol)
            val fetched = news + filings
            val classified = ClaudeClient.classify(symbol, ticker?.companyName, fetched)
            articles = fetched.map { classified[it.id] ?: it }
            priceHistory = AlphaVantageClient.dailyCloses(symbol)
            refreshState = RefreshState.Done("Fetched ${fetched.size} articles.")
        }
    }

    LaunchedEffect(symbol) { refresh() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("←") }
                Column {
                    Text(symbol, style = MaterialTheme.typography.titleLarge)
                    ticker?.companyName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            TextButton(onClick = ::refresh) { Text("Refresh") }
        }

        when (val state = refreshState) {
            RefreshState.Refreshing -> Text("Refreshing…", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            is RefreshState.Done -> Text(state.message, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            RefreshState.Idle -> {}
        }

        PriceChart(priceHistory)

        if (articles.isEmpty() && refreshState != RefreshState.Refreshing) {
            Text(
                "No articles for $symbol — check that the proxy has Finnhub/EDGAR keys configured.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(articles, key = { it.id }) { article ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                article.urgency?.let { UrgencyBadge(it) }
                                Text(article.headline, style = MaterialTheme.typography.titleSmall)
                            }
                            article.whyItMatters?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(article.source, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
