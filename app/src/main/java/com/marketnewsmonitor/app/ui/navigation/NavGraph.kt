package com.marketnewsmonitor.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marketnewsmonitor.app.ui.dashboard.DashboardScreen
import com.marketnewsmonitor.app.ui.settings.SettingsScreen
import com.marketnewsmonitor.app.ui.tickerdetail.TickerDetailScreen
import com.marketnewsmonitor.app.ui.watchlist.WatchlistScreen

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Home),
    Watchlist("watchlist", "Watchlist", Icons.Filled.List),
    Settings("settings", "Settings", Icons.Filled.Settings),
}

private const val TICKER_DETAIL_ROUTE = "ticker"

@Composable
fun MarketNewsMonitorNavHost(startTickerSymbol: String? = null) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onTopLevelDestination = TopLevelDestination.entries.any { it.route == backStackEntry?.destination?.route }

    // Widget-tap deep link (see MainActivity) — runs once; back still returns
    // to the default Dashboard start destination underneath.
    LaunchedEffect(startTickerSymbol) {
        if (!startTickerSymbol.isNullOrBlank()) {
            navController.navigate("$TICKER_DETAIL_ROUTE/$startTickerSymbol")
        }
    }

    Scaffold(
        bottomBar = { if (onTopLevelDestination) AppBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(onTickerClick = { symbol -> navController.navigate("$TICKER_DETAIL_ROUTE/$symbol") })
            }
            composable(TopLevelDestination.Watchlist.route) {
                WatchlistScreen(onTickerClick = { symbol -> navController.navigate("$TICKER_DETAIL_ROUTE/$symbol") })
            }
            composable(TopLevelDestination.Settings.route) { SettingsScreen() }
            composable("$TICKER_DETAIL_ROUTE/{symbol}") { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol").orEmpty()
                TickerDetailScreen(symbol = symbol, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
