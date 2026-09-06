package com.marketnewsmonitor.app.data.remote.edgar

import com.marketnewsmonitor.app.data.local.entity.Ticker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class FakeEdgarApi(
    private val cikMap: Map<String, EdgarTickerCikEntry> = emptyMap(),
    private val submissionsByCik: Map<String, EdgarSubmissionsResponse> = emptyMap(),
    private val failCikMap: Boolean = false,
) : EdgarApi {
    var cikMapCallCount = 0
        private set

    override suspend fun getTickerCikMap(userAgent: String): Map<String, EdgarTickerCikEntry> {
        cikMapCallCount++
        if (failCikMap) throw java.io.IOException("network down")
        return cikMap
    }

    override suspend fun getSubmissions(cik: String, userAgent: String): EdgarSubmissionsResponse =
        submissionsByCik[cik] ?: EdgarSubmissionsResponse()
}

class EdgarSourceTest {

    private val ticker = Ticker("AAPL", "Apple Inc.", 0L)
    private val cikMap = mapOf("0" to EdgarTickerCikEntry(cikStr = 320193, ticker = "AAPL", title = "Apple Inc."))

    @Test
    fun `resolves cik case-insensitively and fetches submissions`() = runBlocking {
        val submissions = EdgarSubmissionsResponse(
            filings = EdgarFilings(
                recent = EdgarRecentFilings(
                    accessionNumber = listOf("0000320193-24-000010"),
                    filingDate = listOf(LocalDate.now().toString()),
                    form = listOf("8-K"),
                    primaryDocument = listOf("form8k.htm"),
                ),
            ),
        )
        val api = FakeEdgarApi(cikMap = cikMap, submissionsByCik = mapOf("0000320193" to submissions))
        val source = EdgarSource(api) { "test-agent" }

        val articles = source.fetch(ticker)

        assertEquals(1, articles.size)
        assertEquals("sec_edgar", articles.first().sourceId)
        assertTrue(articles.first().url.contains("320193"))
    }

    @Test
    fun `returns empty list when ticker has no known cik`() = runBlocking {
        val api = FakeEdgarApi(cikMap = emptyMap())
        val source = EdgarSource(api) { "test-agent" }

        assertTrue(source.fetch(ticker).isEmpty())
    }

    @Test
    fun `caches the cik map across multiple fetches`() = runBlocking {
        val api = FakeEdgarApi(cikMap = cikMap)
        val source = EdgarSource(api) { "test-agent" }

        source.fetch(ticker)
        source.fetch(ticker)

        assertEquals(1, api.cikMapCallCount)
    }

    @Test
    fun `does not cache a failed cik map fetch, so a later call retries`() = runBlocking {
        val api = FakeEdgarApi(failCikMap = true)
        val source = EdgarSource(api) { "test-agent" }

        source.fetch(ticker)
        source.fetch(ticker)

        assertEquals(2, api.cikMapCallCount)
    }

    @Test
    fun `mapEdgarFilings keeps only relevant forms within the lookback window`() {
        val recent = EdgarRecentFilings(
            accessionNumber = listOf("0000320193-24-000001", "0000320193-24-000002", "0000320193-24-000003"),
            filingDate = listOf(
                LocalDate.now().toString(),
                LocalDate.now().minusDays(30).toString(),
                LocalDate.now().toString(),
            ),
            form = listOf("8-K", "8-K", "10-Q"),
            primaryDocument = listOf("a.htm", "b.htm", "c.htm"),
        )

        val articles = mapEdgarFilings("AAPL", "0000320193", recent, cutoffDate = LocalDate.now().minusDays(7))

        assertEquals(1, articles.size)
        assertEquals("Form 8-K filed", articles.first().headline)
    }

    @Test
    fun `mapEdgarFilings builds the archives url without leading zeros or dashes`() {
        val recent = EdgarRecentFilings(
            accessionNumber = listOf("0000320193-24-000001"),
            filingDate = listOf(LocalDate.now().toString()),
            form = listOf("4"),
            primaryDocument = listOf("form4.xml"),
        )

        val article = mapEdgarFilings("AAPL", "0000320193", recent, cutoffDate = LocalDate.now().minusDays(7)).first()

        assertEquals(
            "https://www.sec.gov/Archives/edgar/data/320193/000032019324000001/form4.xml",
            article.url,
        )
    }

    @Test
    fun `buildEdgarUserAgent falls back to the generic identifier when profile is blank`() {
        assertEquals(EdgarSource.DEFAULT_USER_AGENT, buildEdgarUserAgent("", ""))
        assertEquals(EdgarSource.DEFAULT_USER_AGENT, buildEdgarUserAgent("   ", "  "))
    }

    @Test
    fun `buildEdgarUserAgent includes whatever profile fields are set`() {
        assertEquals("MarketNewsMonitor (Shreejit shree@example.com)", buildEdgarUserAgent("Shreejit", "shree@example.com"))
        assertEquals("MarketNewsMonitor (Shreejit)", buildEdgarUserAgent("Shreejit", ""))
    }
}
