package com.marketnewsmonitor.app

import android.app.Application
import androidx.work.Configuration
import com.marketnewsmonitor.app.work.NewsPollWorkerFactory

class MarketNewsMonitorApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // WorkManager auto-detects this and uses it instead of the default init —
    // its documented mechanism for handing Workers our own dependencies.
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(
                NewsPollWorkerFactory(container.tickerRepository, container.newsRepository, container.notificationHelper),
            )
            .build()
}
