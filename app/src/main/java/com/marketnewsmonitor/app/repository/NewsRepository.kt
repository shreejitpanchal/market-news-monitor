package com.marketnewsmonitor.app.repository

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import kotlinx.coroutines.flow.Flow

class NewsRepository(
    private val articleDao: ArticleDao,
    private val registry: NewsSourceRegistry,
) {
    fun observeArticles(symbol: String): Flow<List<Article>> = articleDao.observeForTicker(symbol)

    /**
     * Fetches from every registered source, isolating failures per source —
     * one source erroring (bad key, network blip, HTTP error) must not block
     * articles from the others (fail-loud-vs-skip-deliberately).
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
        return RefreshResult(fetchedCount = articles.size, failures = failures)
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
}

data class RefreshResult(val fetchedCount: Int, val failures: List<String>)
