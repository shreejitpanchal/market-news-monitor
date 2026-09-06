package com.marketnewsmonitor.app.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.marketnewsmonitor.app.data.notifications.NotificationHelper
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository

/**
 * Hands [NewsPollWorker] its dependencies from [com.marketnewsmonitor.app.AppContainer]
 * — WorkManager's documented mechanism for manual DI into Workers, no Hilt needed.
 */
class NewsPollWorkerFactory(
    private val tickerRepository: TickerRepository,
    private val newsRepository: NewsRepository,
    private val notificationHelper: NotificationHelper,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        NewsPollWorker::class.java.name ->
            NewsPollWorker(appContext, workerParameters, tickerRepository, newsRepository, notificationHelper)
        else -> null
    }
}
