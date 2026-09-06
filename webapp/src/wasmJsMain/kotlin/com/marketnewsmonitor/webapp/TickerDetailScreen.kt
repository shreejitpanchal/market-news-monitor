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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TickerDetailScreen(symbol: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ticker = SampleData.tickers.firstOrNull { it.symbol == symbol }
    val articles = SampleData.articlesBySymbol[symbol].orEmpty()
    val priceHistory = SampleData.priceHistory(symbol)

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
                    ticker?.let { Text(it.companyName, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        PriceChart(priceHistory)

        if (articles.isEmpty()) {
            Text(
                "No sample articles for $symbol.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(articles) { article ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                UrgencyBadge(article.urgency)
                                Text(article.headline, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(article.whyItMatters, style = MaterialTheme.typography.bodyMedium)
                            Text("${article.source} · ${article.dateLabel}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
