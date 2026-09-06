package com.marketnewsmonitor.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marketnewsmonitor.app.data.notifications.DigestNotifier
import com.marketnewsmonitor.app.data.remote.claude.DigestGenerator
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository

/**
 * Once-a-day, whole-watchlist digest (see [DigestScheduler] for timing).
 * Actual per-ticker logic lives in [DigestRunner] so it's testable without a
 * real Context.
 */
class DigestWorker(
    context: Context,
    params: WorkerParameters,
    private val tickerRepository: TickerRepository,
    private val newsRepository: NewsRepository,
    private val digestGenerator: DigestGenerator,
    private val digestNotifier: DigestNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DigestRunner(tickerRepository, newsRepository, digestGenerator, digestNotifier).run()
        return Result.success()
    }
}
