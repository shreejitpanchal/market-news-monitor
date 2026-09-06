package com.marketnewsmonitor.app.repository

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.NewsSource
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeArticleDao : ArticleDao {
    // Keyed by id so re-inserts (OnConflictStrategy.IGNORE in the real DAO)
    // don't clobber an already-notified row's state.
    private val byId = LinkedHashMap<String, Article>()
    val inserted: List<Article> get() = byId.values.toList()

    override fun observeForTicker(symbol: String): Flow<List<Article>> =
        MutableStateFlow(byId.values.filter { it.tickerSymbol == symbol })

    override suspend fun insertAll(articles: List<Article>) {
        for (article in articles) {
            byId.putIfAbsent(article.id, article)
        }
    }

    override suspend fun getUnnotifiedSince(symbol: String, sinceMillis: Long): List<Article> =
        byId.values.filter { it.tickerSymbol == symbol && !it.notified && it.publishedAt >= sinceMillis }

    override suspend fun markNotified(ids: List<String>) {
        for (id in ids) {
            byId[id]?.let { byId[id] = it.copy(notified = true) }
        }
    }
}

private class FakeNewsSource(override val id: String, private val result: () -> List<Article>) : NewsSource {
    override suspend fun fetch(ticker: Ticker): List<Article> = result()
}

private class FailingNewsSource(override val id: String, private val error: Exception) : NewsSource {
    override suspend fun fetch(ticker: Ticker): List<Article> = throw error
}

class NewsRepositoryTest {

    private val ticker = Ticker("AAPL", "Apple Inc.", 0L)

    private fun article(sourceId: String, url: String) = Article(
        id = "$sourceId|$url",
        tickerSymbol = "AAPL",
        sourceId = sourceId,
        headline = "Headline from $sourceId",
        url = url,
        publishedAt = 1L,
    )

    @Test
    fun `merges articles from every registered source`() = runBlocking {
        val dao = FakeArticleDao()
        val registry = NewsSourceRegistry(
            listOf(
                FakeNewsSource("finnhub") { listOf(article("finnhub", "https://a")) },
                FakeNewsSource("google_news_rss") { listOf(article("google_news_rss", "https://b")) },
            ),
        )
        val repository = NewsRepository(dao, registry)

        val result = repository.refresh(ticker)

        assertEquals(2, result.fetchedCount)
        assertTrue(result.failures.isEmpty())
        assertEquals(2, dao.inserted.size)
    }

    @Test
    fun `one failing source does not block articles from the others`() = runBlocking {
        val dao = FakeArticleDao()
        val registry = NewsSourceRegistry(
            listOf(
                FailingNewsSource("finnhub", IllegalStateException("missing API key")),
                FakeNewsSource("google_news_rss") { listOf(article("google_news_rss", "https://b")) },
            ),
        )
        val repository = NewsRepository(dao, registry)

        val result = repository.refresh(ticker)

        assertEquals(1, result.fetchedCount)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures.first().contains("finnhub"))
        assertEquals(1, dao.inserted.size)
    }

    @Test
    fun `does not touch the dao when every source returns nothing`() = runBlocking {
        val dao = FakeArticleDao()
        val registry = NewsSourceRegistry(listOf(FakeNewsSource("finnhub") { emptyList() }))
        val repository = NewsRepository(dao, registry)

        repository.refresh(ticker)

        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `getUnnotifiedRecentArticles excludes articles outside the freshness window`() = runBlocking {
        val dao = FakeArticleDao()
        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(
                article("finnhub", "https://fresh").copy(publishedAt = now),
                article("finnhub", "https://stale").copy(publishedAt = now - java.util.concurrent.TimeUnit.DAYS.toMillis(3)),
            ),
        )
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()))

        val eligible = repository.getUnnotifiedRecentArticles("AAPL", java.util.concurrent.TimeUnit.DAYS.toMillis(1))

        assertEquals(1, eligible.size)
        assertEquals("https://fresh", eligible.first().url)
    }

    @Test
    fun `markNotified excludes an article from future eligibility`() = runBlocking {
        val dao = FakeArticleDao()
        dao.insertAll(listOf(article("finnhub", "https://a")))
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()))
        val window = java.util.concurrent.TimeUnit.DAYS.toMillis(1)

        val beforeMark = repository.getUnnotifiedRecentArticles("AAPL", window)
        repository.markNotified(beforeMark)
        val afterMark = repository.getUnnotifiedRecentArticles("AAPL", window)

        assertEquals(1, beforeMark.size)
        assertTrue(afterMark.isEmpty())
    }
}
