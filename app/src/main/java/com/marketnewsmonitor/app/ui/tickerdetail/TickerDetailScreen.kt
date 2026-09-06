package com.marketnewsmonitor.app.ui.tickerdetail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marketnewsmonitor.app.ui.components.UrgencyBadge
import java.text.DateFormat
import java.util.Date

@Composable
fun TickerDetailScreen(symbol: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TickerDetailViewModel = viewModel(factory = TickerDetailViewModel.factory(symbol))
    val ticker by viewModel.ticker.collectAsState()
    val articles by viewModel.articles.collectAsState()
    val priceHistory by viewModel.priceHistory.collectAsState()
    val refreshState by viewModel.refreshState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(symbol) { viewModel.refresh() }

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(symbol, style = MaterialTheme.typography.titleLarge)
                    ticker?.companyName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        if (priceHistory.isNotEmpty()) {
            PriceChart(priceHistory)
        }

        when (val state = refreshState) {
            RefreshUiState.Refreshing -> Text(
                "Refreshing…",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            is RefreshUiState.Error -> Text(
                state.message,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            is RefreshUiState.Done -> Text(
                state.message,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            RefreshUiState.Idle -> {}
        }

        if (articles.isEmpty() && refreshState == RefreshUiState.Refreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (articles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No articles yet — tap refresh to fetch news for $symbol.")
            }
        } else {
            val groups = groupArticlesForDisplay(articles)
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.primary.id }) { group ->
                    ArticleGroupRow(group, onOpenUrl = ::openUrl)
                }
            }
        }
    }
}

@Composable
private fun ArticleGroupRow(group: ArticleGroup, onOpenUrl: (String) -> Unit) {
    val article = group.primary
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenUrl(article.url) }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrgencyBadge(article.urgency)
                Text(article.headline, style = MaterialTheme.typography.titleSmall)
            }
            article.whyItMatters?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "${article.sourceId} · ${DateFormat.getDateInstance().format(Date(article.publishedAt))}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (group.alsoReportedBy.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Also:", style = MaterialTheme.typography.bodySmall)
                    group.alsoReportedBy.forEachIndexed { index, other ->
                        if (index > 0) Text("·", style = MaterialTheme.typography.bodySmall)
                        Text(
                            other.sourceId,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { onOpenUrl(other.url) },
                        )
                    }
                }
            }
        }
    }
}
