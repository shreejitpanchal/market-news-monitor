package com.marketnewsmonitor.webapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class AlphaVantageDailyResponse(
    @SerialName("Time Series (Daily)") val timeSeries: Map<String, AlphaVantageDailyBar>? = null,
)

@Serializable
private data class AlphaVantageDailyBar(@SerialName("4. close") val close: String = "")

/**
 * Goes through the local proxy, which attaches the Alpha Vantage key -- see
 * CLAUDE.md's webapp/server decision. Ported from app/src/main/java/.../
 * alphavantage/AlphaVantagePriceHistoryProvider.kt, minus the 3-month
 * cutoff filter: outputsize=compact already caps at ~100 trading days
 * (~4.5 months) from Alpha Vantage itself, which is close enough for a
 * simple line chart without needing date arithmetic on this Wasm target.
 */
object AlphaVantageClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dailyCloses(symbol: String): List<PricePoint> = try {
        val text = proxyGet("/proxy/alphavantage/query?function=TIME_SERIES_DAILY&outputsize=compact&symbol=$symbol")
        val response = json.decodeFromString<AlphaVantageDailyResponse>(text)
        response.timeSeries.orEmpty()
            .mapNotNull { (date, bar) -> bar.close.toDoubleOrNull()?.let { PricePoint(date, it) } }
            .sortedBy { it.date }
    } catch (e: Exception) {
        emptyList()
    }
}
