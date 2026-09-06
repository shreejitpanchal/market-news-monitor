package com.marketnewsmonitor.app.repository

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.dao.TickerUrgency
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.NewsSource
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassification
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

private class FakeArticleDao : ArticleDao {
    // Keyed by id so re-inserts (OnConflictStrategy.IGNORE in the real DAO)
    // don't clobber an already-notified/classified row's state.
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

    override suspend fun getUnclassified(symbol: String, limit: Int): List<Article> =
        byId.values.filter { it.tickerSymbol == symbol && it.urgency == null }.take(limit)

    override suspend fun updateClassification(id: String, urgency: String, whyItMatters: String) {
        byId[id]?.let { byId[id] = it.copy(urgency = urgency, whyItMatters = whyItMatters) }
    }

    override suspend fun getLatestUrgency(symbol: String, sinceMillis: Long): String? {
        val severity = listOf("hot", "warm", "calm")
        return byId.values
            .filter { it.tickerSymbol == symbol && it.urgency != null && it.publishedAt >= sinceMillis }
            .minByOrNull { severity.indexOf(it.urgency) }
            ?.urgency
    }

    override fun observeUrgenciesSince(sinceMillis: Long): Flow<List<TickerUrgency>> =
        MutableStateFlow(
            byId.values
                .filter { it.urgency != null && it.publishedAt >= sinceMillis }
                .map { TickerUrgency(it.tickerSymbol, it.urgency!!) },
        )
}

private class FakeNewsSource(override val id: String, private val result: () -> List<Article>) : NewsSource {
    override suspend fun fetch(ticker: Ticker): List<Article> = result()
}

private class FailingNewsSource(override val id: String, private val error: Exception) : NewsSource {
    override suspend fun fetch(ticker: Ticker): List<Article> = throw error
}

/** No classification by default; tests that care pass a non-empty [results] map. */
private class FakeArticleClassifier(private val results: Map<String, ArticleClassification> = emptyMap()) : ArticleClassifier {
    var lastBatchSize = -1
        private set

    override suspend fun classify(ticker: Ticker, articles: List<Article>): Map<String, ArticleClassification> {
        lastBatchSize = articles.size
        return results
    }
}

class NewsRepositoryTest {

    private val ticker = Ticker("AAPL", "Apple Inc.", 0L)
    private val noopClassifier = FakeArticleClassifier()

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
        val repository = NewsRepository(dao, registry, noopClassifier)

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
        val repository = NewsRepository(dao, registry, noopClassifier)

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
        val repository = NewsRepository(dao, registry, noopClassifier)

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
                article("finnhub", "https://stale").copy(publishedAt = now - TimeUnit.DAYS.toMillis(3)),
            ),
        )
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()), noopClassifier)

        val eligible = repository.getUnnotifiedRecentArticles("AAPL", TimeUnit.DAYS.toMillis(1))

        assertEquals(1, eligible.size)
        assertEquals("https://fresh", eligible.first().url)
    }

    @Test
    fun `markNotified excludes an article from future eligibility`() = runBlocking {
        val dao = FakeArticleDao()
        dao.insertAll(listOf(article("finnhub", "https://a")))
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()), noopClassifier)
        val window = TimeUnit.DAYS.toMillis(1)

        val beforeMark = repository.getUnnotifiedRecentArticles("AAPL", window)
        repository.markNotified(beforeMark)
        val afterMark = repository.getUnnotifiedRecentArticles("AAPL", window)

        assertEquals(1, beforeMark.size)
        assertTrue(afterMark.isEmpty())
    }

    @Test
    fun `refresh classifies newly fetched articles and writes results back`() = runBlocking {
        val dao = FakeArticleDao()
        val registry = NewsSourceRegistry(listOf(FakeNewsSource("finnhub") { listOf(article("finnhub", "https://a")) }))
        val classifier = FakeArticleClassifier(
            results = mapOf("finnhub|https://a" to ArticleClassification("hot", "Earnings beat")),
        )
        val repository = NewsRepository(dao, registry, classifier)

        repository.refresh(ticker)

        val classified = dao.inserted.first()
        assertEquals("hot", classified.urgency)
        assertEquals("Earnings beat", classified.whyItMatters)
    }

    @Test
    fun `refresh also retries articles left unclassified from a previous attempt`() = runBlocking {
        val dao = FakeArticleDao()
        dao.insertAll(listOf(article("finnhub", "https://old"))) // urgency still null, as if classification failed last time
        val classifier = FakeArticleClassifier()
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()), classifier)

        repository.refresh(ticker)

        assertEquals(1, classifier.lastBatchSize)
    }

    @Test
    fun `refresh skips classification entirely when nothing is unclassified`() = runBlocking {
        val dao = FakeArticleDao()
        val classifier = FakeArticleClassifier()
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()), classifier)

        repository.refresh(ticker)

        assertEquals(-1, classifier.lastBatchSize)
    }

    @Test
    fun `getLatestUrgency picks the most severe classified article in the window`() = runBlocking {
        val dao = FakeArticleDao()
        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(
                article("finnhub", "https://warm").copy(publishedAt = now, urgency = "warm"),
                article("finnhub", "https://hot").copy(publishedAt = now, urgency = "hot"),
            ),
        )
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()), noopClassifier)

        assertEquals("hot", repository.getLatestUrgency("AAPL", TimeUnit.DAYS.toMillis(1)))
    }

    @Test
    fun `getLatestUrgency ignores articles outside the window`() = runBlocking {
        val dao = FakeArticleDao()
        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(article("finnhub", "https://old").copy(publishedAt = now - TimeUnit.DAYS.toMillis(3), urgency = "hot")),
        )
        val repository = NewsRepository(dao, NewsSourceRegistry(emptyList()), noopClassifier)

        assertNull(repository.getLatestUrgency("AAPL", TimeUnit.DAYS.toMillis(1)))
    }
}
