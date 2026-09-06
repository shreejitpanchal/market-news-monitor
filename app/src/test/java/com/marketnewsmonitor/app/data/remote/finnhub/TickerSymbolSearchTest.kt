package com.marketnewsmonitor.app.data.remote.finnhub

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFinnhubApi(
    private val response: FinnhubSymbolSearchResponse = FinnhubSymbolSearchResponse(),
    private val fail: Boolean = false,
) : FinnhubApi {
    var lastQuery: String? = null
        private set

    override suspend fun companyNews(symbol: String, from: String, to: String, token: String): List<FinnhubNewsDto> =
        emptyList()

    override suspend fun earningsCalendar(symbol: String, from: String, to: String, token: String): FinnhubEarningsCalendarResponse =
        FinnhubEarningsCalendarResponse()

    override suspend fun symbolSearch(query: String, token: String): FinnhubSymbolSearchResponse {
        lastQuery = query
        if (fail) throw java.io.IOException("network down")
        return response
    }
}

class TickerSymbolSearchTest {

    @Test
    fun `returns no suggestions and skips the call when no api key is set`() = runBlocking {
        val api = FakeFinnhubApi(response = FinnhubSymbolSearchResponse(listOf(FinnhubSymbolSearchResult("AAPL", "Apple Inc"))))
        val search = TickerSymbolSearch(api) { null }

        val results = search.search("apple")

        assertTrue(results.isEmpty())
        assertEquals(null, api.lastQuery)
    }

    @Test
    fun `returns no suggestions for a blank query`() = runBlocking {
        val api = FakeFinnhubApi()
        val search = TickerSymbolSearch(api) { "key" }

        val results = search.search("  ")

        assertTrue(results.isEmpty())
        assertEquals(null, api.lastQuery)
    }

    @Test
    fun `maps a successful response into suggestions`() = runBlocking {
        val api = FakeFinnhubApi(response = FinnhubSymbolSearchResponse(listOf(FinnhubSymbolSearchResult("AAPL", "Apple Inc"))))
        val search = TickerSymbolSearch(api) { "key" }

        val results = search.search("apple")

        assertEquals(listOf(TickerSuggestion("AAPL", "Apple Inc")), results)
        assertEquals("apple", api.lastQuery)
    }

    @Test
    fun `fails closed to an empty list on a network error`() = runBlocking {
        val search = TickerSymbolSearch(FakeFinnhubApi(fail = true)) { "key" }

        assertTrue(search.search("apple").isEmpty())
    }

    @Test
    fun `mapFinnhubSymbolSearch drops foreign-exchange listings with a dot in the symbol`() {
        val response = FinnhubSymbolSearchResponse(
            listOf(
                FinnhubSymbolSearchResult("AAPL", "Apple Inc"),
                FinnhubSymbolSearchResult("AAPL.SW", "Apple Inc (Swiss)"),
            ),
        )

        val suggestions = mapFinnhubSymbolSearch(response)

        assertEquals(listOf(TickerSuggestion("AAPL", "Apple Inc")), suggestions)
    }

    @Test
    fun `mapFinnhubSymbolSearch drops entries with a blank symbol and de-duplicates`() {
        val response = FinnhubSymbolSearchResponse(
            listOf(
                FinnhubSymbolSearchResult("", "No symbol"),
                FinnhubSymbolSearchResult("AAPL", "Apple Inc"),
                FinnhubSymbolSearchResult("AAPL", "Apple Inc (duplicate)"),
            ),
        )

        val suggestions = mapFinnhubSymbolSearch(response)

        assertEquals(1, suggestions.size)
        assertEquals("AAPL", suggestions.first().symbol)
    }

    @Test
    fun `mapFinnhubSymbolSearch caps results at MAX_SUGGESTIONS`() {
        val response = FinnhubSymbolSearchResponse(
            (1..20).map { FinnhubSymbolSearchResult("SYM$it", "Company $it") },
        )

        val suggestions = mapFinnhubSymbolSearch(response)

        assertEquals(TickerSymbolSearch.MAX_SUGGESTIONS, suggestions.size)
    }
}
