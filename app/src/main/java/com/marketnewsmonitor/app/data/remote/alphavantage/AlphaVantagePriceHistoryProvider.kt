package com.marketnewsmonitor.app.data.remote.alphavantage

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class PricePoint(val date: LocalDate, val close: Double)

/** Extracted so [com.marketnewsmonitor.app.ui.tickerdetail.TickerDetailViewModel] is testable without a real network call. */
interface PriceHistoryProvider {
    suspend fun getDailyCloses(symbol: String): List<PricePoint>
}

/**
 * Alpha Vantage's free tier (25 requests/day) is reserved for this kind of
 * on-demand call — loaded once when a ticker's detail screen opens, never
 * polled in the background — unlike Finnhub, whose free tier no longer
 * serves historical candle data for US stocks.
 */
class AlphaVantagePriceHistoryProvider(
    private val api: AlphaVantageApi,
    private val apiKeyProvider: () -> String?,
) : PriceHistoryProvider {

    override suspend fun getDailyCloses(symbol: String): List<PricePoint> {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return try {
            val cutoff = LocalDate.now(ZoneOffset.UTC).minusMonths(LOOKBACK_MONTHS)
            mapAlphaVantageDailyResponse(api.dailyTimeSeries(symbol, key), cutoff)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val LOOKBACK_MONTHS = 3L
    }
}

/**
 * Pure, kept separate from [AlphaVantagePriceHistoryProvider.getDailyCloses]
 * so it's testable without a network call. A rate-limited or invalid-key
 * response omits "Time Series (Daily)" entirely (Alpha Vantage returns a
 * "Note"/"Information" string instead) — that shows up here as a null
 * [AlphaVantageDailyResponse.timeSeries], mapped to an empty list rather
 * than an error.
 */
fun mapAlphaVantageDailyResponse(response: AlphaVantageDailyResponse, cutoffDate: LocalDate): List<PricePoint> =
    response.timeSeries.orEmpty()
        .mapNotNull { (dateText, bar) ->
            val date = try {
                LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: Exception) {
                return@mapNotNull null
            }
            val close = bar.close.toDoubleOrNull() ?: return@mapNotNull null
            if (date.isBefore(cutoffDate)) null else PricePoint(date, close)
        }
        .sortedBy { it.date }
