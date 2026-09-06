package com.marketnewsmonitor.app.work

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.dao.TickerDao
import com.marketnewsmonitor.app.data.local.dao.TickerUrgency
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.data.notifications.ArticleNotifier
import com.marketnewsmonitor.app.data.remote.NewsSource
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassification
import com.marketnewsmonitor.app.data.remote.claude.ArticleClassifier
import com.marketnewsmonitor.app.data.remote.finnhub.EarningsCalendarProvider
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

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

private class FakeArticleDao : ArticleDao {
    private val byId = LinkedHashMap<String, Article>()

    override fun observeForTicker(symbol: String): Flow<List<Article>> =
        MutableStateFlow(byId.values.filter { it.tickerSymbol == symbol })

    override suspend fun insertAll(articles: List<Article>) {
        for (article in articles) byId.putIfAbsent(article.id, article)
    }

    override suspend fun getUnnotifiedSince(symbol: String, sinceMillis: Long): List<Article> =
        byId.values.filter { it.tickerSymbol == symbol && !it.notified && it.publishedAt >= sinceMillis }

    override suspend fun markNotified(ids: List<String>) {
        for (id in ids) byId[id]?.let { byId[id] = it.copy(notified = true) }
    }

    override suspend fun getUnclassified(symbol: String, limit: Int): List<Article> =
        byId.values.filter { it.tickerSymbol == symbol && it.urgency == null }.take(limit)

    override suspend fun updateClassification(id: String, urgency: String, whyItMatters: String) {
        byId[id]?.let { byId[id] = it.copy(urgency = urgency, whyItMatters = whyItMatters) }
    }

    override suspend fun getLatestUrgency(symbol: String, sinceMillis: Long): String? = null

    override fun observeUrgenciesSince(sinceMillis: Long): Flow<List<TickerUrgency>> = MutableStateFlow(emptyList())

    override suspend fun getRecentByUrgencies(symbol: String, sinceMillis: Long, urgencies: List<String>): List<Article> =
        byId.values.filter { it.tickerSymbol == symbol && it.urgency in urgencies && it.publishedAt >= sinceMillis }
}

private class NoopArticleClassifier : ArticleClassifier {
    override suspend fun classify(ticker: Ticker, articles: List<Article>): Map<String, ArticleClassification> = emptyMap()
}

private class FakeArticleNotifier(private val throwFor: String? = null) : ArticleNotifier {
    val notifiedTickers = mutableListOf<String>()

    override fun notifyNewArticles(ticker: Ticker, articles: List<Article>): Boolean {
        if (ticker.symbol == throwFor) throw IllegalStateException("boom")
        notifiedTickers += ticker.symbol
        return true
    }
}

private fun article(
    symbol: String,
    sourceId: String,
    url: String,
    publishedAt: Long = System.currentTimeMillis(),
    urgency: String? = Urgency.HOT, // classified+hot by default, so baseline notification gating doesn't need to be every test's concern
) = Article(
    id = "$sourceId|$url",
    tickerSymbol = symbol,
    sourceId = sourceId,
    headline = "Headline for $symbol",
    url = url,
    publishedAt = publishedAt,
    urgency = urgency,
)

private class FakeEarningsCalendarProvider(private val nearEarnings: Set<String> = emptySet()) : EarningsCalendarProvider {
    override suspend fun isNearEarnings(ticker: Ticker): Boolean = ticker.symbol in nearEarnings
}

/** Returns one article per ticker, keyed by that ticker's own symbol. */
private fun perTickerSource(sourceId: String = "finnhub") = object : NewsSource {
    override val id = sourceId
    override suspend fun fetch(ticker: Ticker): List<Article> = listOf(article(ticker.symbol, sourceId, "https://${ticker.symbol}"))
}

class NewsPollRunnerTest {

    @Test
    fun `skips muted tickers entirely`() = runBlocking {
        val tickerDao = FakeTickerDao(
            listOf(Ticker("AAPL", "Apple", 0L, muted = true), Ticker("TSLA", "Tesla", 0L, muted = false)),
        )
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(perTickerSource())), NoopArticleClassifier())
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier, FakeEarningsCalendarProvider())

        runner.pollAll()

        assertEquals(listOf("TSLA"), notifier.notifiedTickers)
    }

    @Test
    fun `notifies and marks notified only for fresh unmuted-ticker articles`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L, muted = false)))
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(perTickerSource())), NoopArticleClassifier())
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier, FakeEarningsCalendarProvider())

        runner.pollAll()

        assertEquals(listOf("AAPL"), notifier.notifiedTickers)
        assertTrue(
            newsRepository.getUnnotifiedRecentArticles("AAPL", TimeUnit.DAYS.toMillis(1), setOf(Urgency.HOT)).isEmpty(),
        )
    }

    @Test
    fun `an unexpected error for one ticker does not stop the rest of the poll`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("BAD", "Bad Co", 0L), Ticker("AAPL", "Apple", 0L)))
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(perTickerSource())), NoopArticleClassifier())
        val notifier = FakeArticleNotifier(throwFor = "BAD")
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier, FakeEarningsCalendarProvider())

        runner.pollAll()

        assertEquals(listOf("AAPL"), notifier.notifiedTickers)
    }

    @Test
    fun `a warm article does not notify at baseline sensitivity`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L)))
        val source = object : NewsSource {
            override val id = "finnhub"
            override suspend fun fetch(ticker: Ticker): List<Article> =
                listOf(article(ticker.symbol, "finnhub", "https://a", urgency = Urgency.WARM))
        }
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(source)), NoopArticleClassifier())
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier, FakeEarningsCalendarProvider())

        runner.pollAll()

        assertTrue(notifier.notifiedTickers.isEmpty())
    }

    @Test
    fun `a warm article does notify when the ticker is near earnings`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L)))
        val source = object : NewsSource {
            override val id = "finnhub"
            override suspend fun fetch(ticker: Ticker): List<Article> =
                listOf(article(ticker.symbol, "finnhub", "https://a", urgency = Urgency.WARM))
        }
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(source)), NoopArticleClassifier())
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(
            TickerRepository(tickerDao),
            newsRepository,
            notifier,
            FakeEarningsCalendarProvider(nearEarnings = setOf("AAPL")),
        )

        runner.pollAll()

        assertEquals(listOf("AAPL"), notifier.notifiedTickers)
    }

    @Test
    fun `a calm article never notifies even near earnings`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L)))
        val source = object : NewsSource {
            override val id = "finnhub"
            override suspend fun fetch(ticker: Ticker): List<Article> =
                listOf(article(ticker.symbol, "finnhub", "https://a", urgency = Urgency.CALM))
        }
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(source)), NoopArticleClassifier())
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(
            TickerRepository(tickerDao),
            newsRepository,
            notifier,
            FakeEarningsCalendarProvider(nearEarnings = setOf("AAPL")),
        )

        runner.pollAll()

        assertTrue(notifier.notifiedTickers.isEmpty())
    }
}
