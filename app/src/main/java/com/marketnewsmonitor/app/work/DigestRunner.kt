package com.marketnewsmonitor.app.work

import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.data.notifications.DigestNotifier
import com.marketnewsmonitor.app.data.remote.claude.DigestGenerator
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import com.marketnewsmonitor.app.repository.collapseClusters
import java.util.concurrent.TimeUnit

/**
 * The actual digest orchestration, extracted from [DigestWorker] so it's
 * testable as a plain class — same reasoning as [NewsPollRunner].
 */
class DigestRunner(
    private val tickerRepository: TickerRepository,
    private val newsRepository: NewsRepository,
    private val digestGenerator: DigestGenerator,
    private val digestNotifier: DigestNotifier,
) {
    suspend fun run() {
        val tickers = tickerRepository.getTickers().filterNot { it.muted }
        // Collapsed per ticker so a story two outlets both reported doesn't
        // get summarized twice in the same digest — no "already digested"
        // state to track, this is purely a prompt-input dedup.
        val notable = tickers.associateWith { ticker ->
            collapseClusters(newsRepository.getRecentNotableArticles(ticker.symbol, WINDOW_MILLIS, NOTABLE_URGENCIES))
        }.filterValues { it.isNotEmpty() }

        if (notable.isEmpty()) return

        val digest = digestGenerator.generateDigest(notable) ?: return
        digestNotifier.notifyDigest(digest)
    }

    companion object {
        val WINDOW_MILLIS: Long = TimeUnit.HOURS.toMillis(24)
        val NOTABLE_URGENCIES = setOf(Urgency.HOT, Urgency.WARM)
    }
}
