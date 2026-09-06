package com.marketnewsmonitor.app.data.remote.finnhub

data class TickerSuggestion(val symbol: String, val name: String)

class TickerSymbolSearch(
    private val api: FinnhubApi,
    private val apiKeyProvider: () -> String?,
) {
    suspend fun search(query: String): List<TickerSuggestion> {
        val token = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return try {
            mapFinnhubSymbolSearch(api.symbolSearch(query, token))
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val MAX_SUGGESTIONS = 8
    }
}

/**
 * Pure mapping, kept separate from [TickerSymbolSearch.search] so it's
 * testable without a network call. Finnhub's search endpoint also returns
 * foreign-exchange listings of the same company (e.g. "AAPL.SW"); those
 * always carry a "." in the symbol, so filtering them out keeps only the
 * primary US-listed ticker, which is what this app's watchlist expects.
 */
fun mapFinnhubSymbolSearch(response: FinnhubSymbolSearchResponse): List<TickerSuggestion> =
    response.result
        .filter { it.symbol.isNotBlank() && !it.symbol.contains(".") }
        .map { TickerSuggestion(symbol = it.symbol, name = it.description) }
        .distinctBy { it.symbol }
        .take(TickerSymbolSearch.MAX_SUGGESTIONS)
