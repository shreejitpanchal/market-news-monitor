package com.marketnewsmonitor.app.data.remote.alphavantage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private class FakeAlphaVantageApi(
    private val response: AlphaVantageDailyResponse = AlphaVantageDailyResponse(),
    private val fail: Boolean = false,
) : AlphaVantageApi {
    override suspend fun dailyTimeSeries(symbol: String, apiKey: String): AlphaVantageDailyResponse {
        if (fail) throw java.io.IOException("network down")
        return response
    }
}

class AlphaVantagePriceHistoryProviderTest {

    @Test
    fun `returns no points and skips the call when no api key is set`() = runBlocking {
        val response = AlphaVantageDailyResponse(mapOf("2024-01-25" to AlphaVantageDailyBar("150.25")))
        val provider = AlphaVantagePriceHistoryProvider(FakeAlphaVantageApi(response)) { null }

        assertTrue(provider.getDailyCloses("AAPL").isEmpty())
    }

    @Test
    fun `maps a successful response into sorted price points`() = runBlocking {
        val response = AlphaVantageDailyResponse(
            mapOf(
                "2024-01-25" to AlphaVantageDailyBar("150.25"),
                "2024-01-24" to AlphaVantageDailyBar("148.00"),
            ),
        )
        val provider = AlphaVantagePriceHistoryProvider(FakeAlphaVantageApi(response)) { "key" }

        val points = provider.getDailyCloses("AAPL")

        assertEquals(listOf(LocalDate.parse("2024-01-24"), LocalDate.parse("2024-01-25")), points.map { it.date })
    }

    @Test
    fun `fails closed to an empty list on a network error`() = runBlocking {
        val provider = AlphaVantagePriceHistoryProvider(FakeAlphaVantageApi(fail = true)) { "key" }

        assertTrue(provider.getDailyCloses("AAPL").isEmpty())
    }

    @Test
    fun `mapAlphaVantageDailyResponse returns empty when Time Series is missing (rate-limited or invalid key)`() {
        val points = mapAlphaVantageDailyResponse(AlphaVantageDailyResponse(timeSeries = null), LocalDate.MIN)

        assertTrue(points.isEmpty())
    }

    @Test
    fun `mapAlphaVantageDailyResponse skips entries older than the cutoff`() {
        val response = AlphaVantageDailyResponse(
            mapOf(
                "2024-01-01" to AlphaVantageDailyBar("100.00"),
                "2024-03-01" to AlphaVantageDailyBar("120.00"),
            ),
        )

        val points = mapAlphaVantageDailyResponse(response, cutoffDate = LocalDate.parse("2024-02-01"))

        assertEquals(1, points.size)
        assertEquals(LocalDate.parse("2024-03-01"), points.first().date)
    }

    @Test
    fun `mapAlphaVantageDailyResponse skips an unparsable close value rather than crashing`() {
        val response = AlphaVantageDailyResponse(
            mapOf(
                "2024-01-25" to AlphaVantageDailyBar("not-a-number"),
                "2024-01-26" to AlphaVantageDailyBar("150.25"),
            ),
        )

        val points = mapAlphaVantageDailyResponse(response, LocalDate.MIN)

        assertEquals(1, points.size)
        assertEquals(150.25, points.first().close, 0.0001)
    }

    @Test
    fun `mapAlphaVantageDailyResponse parses volume, defaulting to 0 when unparsable`() {
        val response = AlphaVantageDailyResponse(
            mapOf(
                "2024-01-25" to AlphaVantageDailyBar(close = "150.25", volume = "1234567"),
                "2024-01-26" to AlphaVantageDailyBar(close = "151.00", volume = "not-a-number"),
            ),
        )

        val points = mapAlphaVantageDailyResponse(response, LocalDate.MIN).associateBy { it.date }

        assertEquals(1_234_567L, points.getValue(LocalDate.parse("2024-01-25")).volume)
        assertEquals(0L, points.getValue(LocalDate.parse("2024-01-26")).volume)
    }

    @Test
    fun `mapAlphaVantageDailyResponse sorts ascending by date`() {
        val response = AlphaVantageDailyResponse(
            mapOf(
                "2024-03-01" to AlphaVantageDailyBar("120.00"),
                "2024-01-01" to AlphaVantageDailyBar("100.00"),
                "2024-02-01" to AlphaVantageDailyBar("110.00"),
            ),
        )

        val points = mapAlphaVantageDailyResponse(response, LocalDate.MIN)

        assertEquals(
            listOf(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-02-01"), LocalDate.parse("2024-03-01")),
            points.map { it.date },
        )
    }
}
