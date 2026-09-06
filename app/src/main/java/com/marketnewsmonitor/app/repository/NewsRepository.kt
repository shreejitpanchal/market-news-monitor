package com.marketnewsmonitor.app.repository

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassifier
import com.marketnewsmonitor.app.data.remote.claude.ClaudeArticleClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NewsRepository(
    private val articleDao: ArticleDao,
    private val registry: NewsSourceRegistry,
    private val classifier: ArticleClassifier,
) {
    fun observeArticles(symbol: String): Flow<List<Article>> = articleDao.observeForTicker(symbol)

    /**
     * Fetches from every registered source, isolating failures per source —
     * one source erroring (bad key, network blip, HTTP error) must not block
     * articles from the others (fail-loud-vs-skip-deliberately) — then
     * classifies whatever's still unclassified for this ticker (including
     * any left over from a previous failed/keyless attempt, not just what
     * was just fetched).
     *
     * The UI doesn't wait on classification separately: it observes
     * [observeArticles] as a Room Flow, which already emits the moment
     * insertAll below commits, then again once classification writes back —
     * articles appear immediately, badges follow.
     */
    suspend fun refresh(ticker: Ticker): RefreshResult {
        val failures = mutableListOf<String>()
        val articles = registry.all().flatMap { source ->
            try {
                source.fetch(ticker)
            } catch (e: Exception) {
                failures += "${source.id}: ${e.message ?: e::class.simpleName}"
                emptyList()
            }
        }
        if (articles.isNotEmpty()) {
            articleDao.insertAll(articles)
        }

        classifyPending(ticker)

        return RefreshResult(fetchedCount = articles.size, failures = failures)
    }

    private suspend fun classifyPending(ticker: Ticker) {
        val unclassified = articleDao.getUnclassified(ticker.symbol, ClaudeArticleClassifier.MAX_ARTICLES_PER_CALL)
        if (unclassified.isEmpty()) return
        val results = classifier.classify(ticker, unclassified)
        for ((id, classification) in results) {
            articleDao.updateClassification(id, classification.urgency, classification.whyItMatters)
        }
    }

    /**
     * Notify-eligible articles for [symbol]: not yet notified, published within
     * [freshnessWindowMillis] of now. Bounds a freshly-added ticker's first poll
     * from dumping a week of backfill as one giant notification.
     */
    suspend fun getUnnotifiedRecentArticles(symbol: String, freshnessWindowMillis: Long): List<Article> =
        articleDao.getUnnotifiedSince(symbol, System.currentTimeMillis() - freshnessWindowMillis)

    suspend fun markNotified(articles: List<Article>) {
        if (articles.isEmpty()) return
        articleDao.markNotified(articles.map { it.id })
    }

    /** Highest-severity urgency among [symbol]'s articles published within the window, for the Dashboard badge. */
    suspend fun getLatestUrgency(symbol: String, freshnessWindowMillis: Long): String? =
        articleDao.getLatestUrgency(symbol, System.currentTimeMillis() - freshnessWindowMillis)

    /** Reactive per-ticker highest-severity urgency, for Dashboard badges that update live as classification completes. */
    fun observeUrgencies(freshnessWindowMillis: Long): Flow<Map<String, String>> =
        articleDao.observeUrgenciesSince(System.currentTimeMillis() - freshnessWindowMillis).map { rows ->
            rows.groupBy { it.tickerSymbol }
                .mapValues { (_, group) -> group.map { it.urgency }.minBy { Urgency.SEVERITY_ORDER.indexOf(it) } }
        }
}

data class RefreshResult(val fetchedCount: Int, val failures: List<String>)
