package com.marketnewsmonitor.webapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class Tab(val label: String) { DASHBOARD("Dashboard"), WATCHLIST("Watchlist"), SETTINGS("Settings") }

/**
 * Tab row mirroring the real app's `TopLevelDestination`
 * (`app/src/main/java/.../ui/navigation/NavGraph.kt`), reimplemented locally
 * — no shared navigation code between the two modules. Picking a ticker
 * (from Dashboard or Watchlist) swaps to a simple in-memory "detail" state
 * instead of a real nav graph, since this is a 4-screen preview, not a
 * full app.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var selectedTab by remember { mutableStateOf(Tab.DASHBOARD) }
            var openTickerSymbol by remember { mutableStateOf<String?>(null) }

            val symbol = openTickerSymbol
            if (symbol != null) {
                TickerDetailScreen(symbol = symbol, onBack = { openTickerSymbol = null })
                return@Surface
            }

            Scaffold(
                topBar = {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab.entries.forEach { tab ->
                            Tab(
                                selected = tab == selectedTab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                val content = Modifier.fillMaxSize().padding(padding)
                when (selectedTab) {
                    Tab.DASHBOARD -> DashboardScreen(onTickerClick = { openTickerSymbol = it }, modifier = content)
                    Tab.WATCHLIST -> WatchlistScreen(onTickerClick = { openTickerSymbol = it }, modifier = content)
                    Tab.SETTINGS -> SettingsScreen(modifier = content)
                }
            }
        }
    }
}
