package com.marketnewsmonitor.app.data.remote.finnhub

import com.marketnewsmonitor.app.data.local.entity.Ticker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFinnhubApi(
    private val response: FinnhubEarningsCalendarResponse = FinnhubEarningsCalendarResponse(),
    private val fail: Boolean = false,
) : FinnhubApi {
    override suspend fun companyNews(symbol: String, from: String, to: String, token: String): List<FinnhubNewsDto> =
        emptyList()

    override suspend fun earningsCalendar(symbol: String, from: String, to: String, token: String): FinnhubEarningsCalendarResponse {
        if (fail) throw java.io.IOException("network down")
        return response
    }

    override suspend fun symbolSearch(query: String, token: String): FinnhubSymbolSearchResponse =
        FinnhubSymbolSearchResponse()
}

class FinnhubEarningsCalendarProviderTest {

    private val ticker = Ticker("AAPL", "Apple Inc.", 0L)

    @Test
    fun `returns false when no api key is set`() = runBlocking {
        val provider = FinnhubEarningsCalendarProvider(FakeFinnhubApi()) { null }

        assertFalse(provider.isNearEarnings(ticker))
    }

    @Test
    fun `returns true when the calendar has an entry in the window`() = runBlocking {
        val response = FinnhubEarningsCalendarResponse(
            earningsCalendar = listOf(FinnhubEarningsEntry(date = "2024-01-25", symbol = "AAPL")),
        )
        val provider = FinnhubEarningsCalendarProvider(FakeFinnhubApi(response = response)) { "key" }

        assertTrue(provider.isNearEarnings(ticker))
    }

    @Test
    fun `returns false when the calendar has no entries`() = runBlocking {
        val provider = FinnhubEarningsCalendarProvider(FakeFinnhubApi()) { "key" }

        assertFalse(provider.isNearEarnings(ticker))
    }

    @Test
    fun `fails closed to false on a network error`() = runBlocking {
        val provider = FinnhubEarningsCalendarProvider(FakeFinnhubApi(fail = true)) { "key" }

        assertFalse(provider.isNearEarnings(ticker))
    }
}
