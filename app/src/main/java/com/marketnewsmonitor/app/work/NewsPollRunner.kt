package com.marketnewsmonitor.app.work

import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.data.notifications.ArticleNotifier
import com.marketnewsmonitor.app.data.remote.finnhub.EarningsCalendarProvider
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import java.util.concurrent.TimeUnit

/**
 * The actual per-poll orchestration, extracted from [NewsPollWorker] so it's
 * testable as a plain class — [androidx.work.CoroutineWorker] needs a real
 * Context/WorkerParameters that aren't available in a plain JVM unit test
 * without Robolectric, which this project doesn't have configured.
 */
class NewsPollRunner(
    private val tickerRepository: TickerRepository,
    private val newsRepository: NewsRepository,
    private val notifier: ArticleNotifier,
    private val earningsCalendarProvider: EarningsCalendarProvider,
) {
    suspend fun pollAll() {
        val tickers = tickerRepository.getTickers().filterNot { it.muted }
        for (ticker in tickers) {
            try {
                newsRepository.refresh(ticker)
                val allowedUrgencies = if (earningsCalendarProvider.isNearEarnings(ticker)) {
                    NEAR_EARNINGS_NOTIFY_URGENCIES
                } else {
                    BASELINE_NOTIFY_URGENCIES
                }
                val eligible = newsRepository.getUnnotifiedRecentArticles(
                    ticker.symbol,
                    NOTIFICATION_FRESHNESS_WINDOW_MILLIS,
                    allowedUrgencies,
                )
                if (eligible.isNotEmpty() && notifier.notifyNewArticles(ticker, eligible)) {
                    newsRepository.markNotified(eligible)
                }
            } catch (e: Exception) {
                // One ticker misbehaving (unexpected error, not just a single
                // source failure — refresh() already isolates those) must not
                // abort the rest of this poll's tickers.
            }
        }
    }

    companion object {
        val NOTIFICATION_FRESHNESS_WINDOW_MILLIS: Long = TimeUnit.HOURS.toMillis(24)

        /** Only "flagged urgent" notifies by default (docs/ARCHITECTURE.md's stated intent). */
        val BASELINE_NOTIFY_URGENCIES = setOf(Urgency.HOT)

        /** Within ±1 day of earnings, "warm" also notifies — more surfaces when it matters most. */
        val NEAR_EARNINGS_NOTIFY_URGENCIES = setOf(Urgency.HOT, Urgency.WARM)
    }
}
