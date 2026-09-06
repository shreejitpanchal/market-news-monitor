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
        setContent {
            MarketNewsMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MarketNewsMonitorNavHost()
                }
            }
        }
    }
}
