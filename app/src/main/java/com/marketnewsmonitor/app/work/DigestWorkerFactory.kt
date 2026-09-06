package com.marketnewsmonitor.app.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.marketnewsmonitor.app.data.notifications.DigestNotifier
import com.marketnewsmonitor.app.data.remote.claude.DigestGenerator
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository

/**
 * Separate from [NewsPollWorkerFactory] and combined with it via
 * [androidx.work.DelegatingWorkerFactory] in
 * [com.marketnewsmonitor.app.MarketNewsMonitorApp] — neither factory needs
 * to know about the other's worker type.
 */
class DigestWorkerFactory(
    private val tickerRepository: TickerRepository,
    private val newsRepository: NewsRepository,
    private val digestGenerator: DigestGenerator,
    private val digestNotifier: DigestNotifier,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        DigestWorker::class.java.name ->
            DigestWorker(appContext, workerParameters, tickerRepository, newsRepository, digestGenerator, digestNotifier)
        else -> null
    }
}
