package com.marketnewsmonitor.app.data.remote.finnhub

import com.marketnewsmonitor.app.data.local.entity.Ticker
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Extracted so [com.marketnewsmonitor.app.work.NewsPollRunner] is testable without a real network call. */
interface EarningsCalendarProvider {
    suspend fun isNearEarnings(ticker: Ticker): Boolean
}

/**
 * Within ±1 day of a ticker's earnings date, alert sensitivity loosens (see
 * NewsPollRunner) — more news matters right around earnings. No caching:
 * unlike EDGAR's whole-market CIK map, this is one cheap per-ticker call per
 * poll, well inside Finnhub's free-tier rate limit for a personal watchlist.
 */
class FinnhubEarningsCalendarProvider(
    private val api: FinnhubApi,
    private val apiKeyProvider: () -> String?,
) : EarningsCalendarProvider {

    override suspend fun isNearEarnings(ticker: Ticker): Boolean {
        val token = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return false
        return try {
            val today = LocalDate.now(ZoneOffset.UTC)
            val response = api.earningsCalendar(
                symbol = ticker.symbol,
                from = today.minusDays(WINDOW_DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE),
                to = today.plusDays(WINDOW_DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE),
                token = token,
            )
            response.earningsCalendar.isNotEmpty()
        } catch (e: Exception) {
            // Fails closed to the stricter baseline sensitivity, not open to spamming.
            false
        }
    }

    companion object {
        private const val WINDOW_DAYS = 1L
    }
}
