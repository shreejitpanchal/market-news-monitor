package com.marketnewsmonitor.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import com.marketnewsmonitor.app.work.DigestWorkerFactory
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
    // Two independent factories (one per worker type) combined via
    // DelegatingWorkerFactory, WorkManager's own composition mechanism, so
    // neither needs to know about the other's worker.
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(
                DelegatingWorkerFactory().apply {
                    addFactory(
                        NewsPollWorkerFactory(
                            container.tickerRepository,
                            container.newsRepository,
                            container.notificationHelper,
                            container.earningsCalendarProvider,
                        ),
                    )
                    addFactory(
                        DigestWorkerFactory(
                            container.tickerRepository,
                            container.newsRepository,
                            container.digestGenerator,
                            container.notificationHelper,
                        ),
                    )
                },
            )
            .build()
}
