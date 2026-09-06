package com.marketnewsmonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.marketnewsmonitor.app.ui.navigation.MarketNewsMonitorNavHost
import com.marketnewsmonitor.app.ui.theme.MarketNewsMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_ACTIVITY_CLEAR_TOP on a standard-launch-mode Activity finishes
        // and recreates it rather than delivering onNewIntent, so reading the
        // extra once here (both widget taps and notification taps use this
        // same flag) always sees the tap that actually launched this instance.
        val startTickerSymbol = intent.getStringExtra(EXTRA_TICKER_SYMBOL)
        setContent {
            MarketNewsMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MarketNewsMonitorNavHost(startTickerSymbol = startTickerSymbol)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TICKER_SYMBOL = "ticker_symbol"
    }
}
