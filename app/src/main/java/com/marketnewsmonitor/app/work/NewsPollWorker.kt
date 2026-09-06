package com.marketnewsmonitor.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marketnewsmonitor.app.data.notifications.ArticleNotifier
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository

/**
 * Periodic background poll. Interval and on/off are Settings-driven (see
 * [com.marketnewsmonitor.app.data.settings.AppPreferences] and
 * [PollScheduler]) — this worker itself doesn't know or care why it's
 * running. Actual per-ticker logic lives in [NewsPollRunner] so it's
 * testable without a real Context.
 */
class NewsPollWorker(
    context: Context,
    params: WorkerParameters,
    private val tickerRepository: TickerRepository,
    private val newsRepository: NewsRepository,
    private val notifier: ArticleNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NewsPollRunner(tickerRepository, newsRepository, notifier).pollAll()
        return Result.success()
    }
}
