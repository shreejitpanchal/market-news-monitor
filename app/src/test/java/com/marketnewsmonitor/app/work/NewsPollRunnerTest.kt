package com.marketnewsmonitor.app.work

import com.marketnewsmonitor.app.data.local.dao.ArticleDao
import com.marketnewsmonitor.app.data.local.dao.TickerDao
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.notifications.ArticleNotifier
import com.marketnewsmonitor.app.data.remote.NewsSource
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
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
}

private class FakeArticleNotifier(private val throwFor: String? = null) : ArticleNotifier {
    val notifiedTickers = mutableListOf<String>()

    override fun notifyNewArticles(ticker: Ticker, articles: List<Article>): Boolean {
        if (ticker.symbol == throwFor) throw IllegalStateException("boom")
        notifiedTickers += ticker.symbol
        return true
    }
}

private fun article(symbol: String, sourceId: String, url: String, publishedAt: Long = System.currentTimeMillis()) =
    Article(
        id = "$sourceId|$url",
        tickerSymbol = symbol,
        sourceId = sourceId,
        headline = "Headline for $symbol",
        url = url,
        publishedAt = publishedAt,
    )

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
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(perTickerSource())))
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier)

        runner.pollAll()

        assertEquals(listOf("TSLA"), notifier.notifiedTickers)
    }

    @Test
    fun `notifies and marks notified only for fresh unmuted-ticker articles`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("AAPL", "Apple", 0L, muted = false)))
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(perTickerSource())))
        val notifier = FakeArticleNotifier()
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier)

        runner.pollAll()

        assertEquals(listOf("AAPL"), notifier.notifiedTickers)
        assertTrue(newsRepository.getUnnotifiedRecentArticles("AAPL", TimeUnit.DAYS.toMillis(1)).isEmpty())
    }

    @Test
    fun `an unexpected error for one ticker does not stop the rest of the poll`() = runBlocking {
        val tickerDao = FakeTickerDao(listOf(Ticker("BAD", "Bad Co", 0L), Ticker("AAPL", "Apple", 0L)))
        val newsRepository = NewsRepository(FakeArticleDao(), NewsSourceRegistry(listOf(perTickerSource())))
        val notifier = FakeArticleNotifier(throwFor = "BAD")
        val runner = NewsPollRunner(TickerRepository(tickerDao), newsRepository, notifier)

        runner.pollAll()

        assertEquals(listOf("AAPL"), notifier.notifiedTickers)
    }
}
