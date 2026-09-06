package com.marketnewsmonitor.app.work

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.dao.TickerDao
import com.marketnewsmonitor.app.data.local.dao.TickerUrgency
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.data.notifications.DigestNotifier
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassification
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassifier
import com.marketnewsmonitor.app.data.remote.claude.DigestGenerator
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTickerDao(initial: List<Ticker> = emptyList()) : TickerDao {
    private val byId = LinkedHashMap<String, Ticker>().apply { initial.forEach { put(it.symbol, it) } }

    override fun observeAll(): Flow<List<Ticker>> = MutableStateFlow(byId.values.toList())
    override suspend fun getAll(): List<Ticker> = byId.values.toList()
    override suspend fun upsert(ticker: Ticker) { byId[ticker.symbol] = ticker }
    override suspend fun delete(ticker: Ticker) { byId.remove(ticker.symbol) }
    override suspend fun deleteAll() { byId.clear() }
    override suspend fun setMuted(symbol: String, muted: Boolean) {
        byId[symbol]?.let { byId[symbol] = it.copy(muted = muted) }
    }
}

private class FakeArticleDao(initial: List<Article> = emptyList()) : ArticleDao {
    private val byId = LinkedHashMap<String, Article>().apply { initial.forEach { put(it.id, it) } }

    override fun observeForTicker(symbol: String): Flow<List<Article>> =
        MutableStateFlow(byId.values.filter { it.tickerSymbol == symbol })

    override suspend fun insertAll(articles: List<Article>) {
        for (article in articles) byId.putIfAbsent(article.id, article)
    }

    override suspend fun getUnnotifiedSince(symbol: String, sinceMillis: Long): List<Article> = emptyList()
    override suspend fun markNotified(ids: List<String>) {}
    override suspend fun getUnclassified(symbol: String, limit: Int): List<Article> = emptyList()
    override suspend fun updateClassification(id: String, urgency: String, whyItMatters: String, clusterId: String?) {}
    override suspend fun getLatestUrgency(symbol: String, sinceMillis: Long): String? = null
    override fun observeUrgenciesSince(sinceMillis: Long): Flow<List<TickerUrgency>> = MutableStateFlow(emptyList())

    override suspend fun getRecentByUrgencies(symbol: String, sinceMillis: Long, urgencies: List<String>): List<Article> =
        byId.values.filter { it.tickerSymbol == symbol && it.urgency in urgencies && it.publishedAt >= sinceMillis }
}

private class NoopArticleClassifier : ArticleClassifier {
    override suspend fun classify(ticker: Ticker, articles: List<Article>): Map<String, ArticleClassification> = emptyMap()
}

private class FakeDigestGenerator(private val result: String? = "digest text") : DigestGenerator {
    var lastInput: Map<Ticker, List<Article>>? = null
        private set

    override suspend fun generateDigest(tickerArticles: Map<Ticker, List<Article>>): String? {
        lastInput = tickerArticles
        return result
    }
}

private class FakeDigestNotifier : DigestNotifier {
    var notified: String? = null
        private set

    override fun notifyDigest(text: String): Boolean {
        notified = text
        return true
    }
}

private fun article(symbol: String, urgency: String?, publishedAt: Long = System.currentTimeMillis()) = Article(
    id = "$symbol-$urgency-$publishedAt",
    tickerSymbol = symbol,
    sourceId = "finnhub",
    headline = "Headline for $symbol",
    url = "https://example.com/$symbol",
    publishedAt = publishedAt,
    urgency = urgency,
)

class DigestRunnerTest {

    @Test
    fun `skips generation and notification when nothing is notable`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L)))
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(emptyList()), NoopArticleClassifier())
        val generator = FakeDigestGenerator()
        val notifier = FakeDigestNotifier()

        DigestRunner(TickerRepository(tickerDao), newsRepository, generator, notifier).run()

        assertNull(generator.lastInput)
        assertNull(notifier.notified)
    }

    @Test
    fun `gathers hot and warm articles across unmuted tickers and notifies the digest`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L), Ticker("TSLA", "Tesla", 0L, muted = true)))
        val articleDao = FakeArticleDao(
            listOf(
                article("AAPL", Urgency.HOT),
                article("AAPL", Urgency.CALM), // excluded: not notable
                article("TSLA", Urgency.HOT), // excluded: ticker muted
            ),
        )
        val newsRepository = NewsRepository(articleDao, NewsSourceRegistry(emptyList()), NoopArticleClassifier())
        val generator = FakeDigestGenerator()
        val notifier = FakeDigestNotifier()

        DigestRunner(TickerRepository(tickerDao), newsRepository, generator, notifier).run()

        val input = generator.lastInput!!
        assertEquals(setOf("AAPL"), input.keys.map { it.symbol }.toSet())
        assertEquals(1, input.values.first().size)
        assertEquals("digest text", notifier.notified)
    }

    @Test
    fun `does not notify when the generator returns null`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L)))
        val articleDao = FakeArticleDao(listOf(article("AAPL", Urgency.HOT)))
        val newsRepository = NewsRepository(articleDao, NewsSourceRegistry(emptyList()), NoopArticleClassifier())
        val generator = FakeDigestGenerator(result = null)
        val notifier = FakeDigestNotifier()

        DigestRunner(TickerRepository(tickerDao), newsRepository, generator, notifier).run()

        assertTrue(notifier.notified == null)
    }

    @Test
    fun `collapses clustered notable articles before they reach the digest prompt`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L)))
        val clustered = article("AAPL", Urgency.HOT).copy(id = "a1", clusterId = "a1")
        val sibling = article("AAPL", Urgency.HOT).copy(id = "a2", clusterId = "a1")
        val articleDao = FakeArticleDao(listOf(clustered, sibling))
        val newsRepository = NewsRepository(articleDao, NewsSourceRegistry(emptyList()), NoopArticleClassifier())
        val generator = FakeDigestGenerator()
        val notifier = FakeDigestNotifier()

        DigestRunner(TickerRepository(tickerDao), newsRepository, generator, notifier).run()

        assertEquals(1, generator.lastInput!!.values.first().size)
    }
}
