package com.marketnewsmonitor.webapp

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists to the browser's localStorage so the watchlist survives a page reload. */
object WatchlistStore {
    private const val STORAGE_KEY = "market_news_monitor_watchlist"
    private val json = Json { ignoreUnknownKeys = true }

    private val _tickers = MutableStateFlow(load())
    val tickers: StateFlow<List<Ticker>> = _tickers.asStateFlow()

    private fun load(): List<Ticker> {
        val raw = localStorage.getItem(STORAGE_KEY) ?: return emptyList()
        return try {
            json.decodeFromString<List<Ticker>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(tickers: List<Ticker>) {
        localStorage.setItem(STORAGE_KEY, json.encodeToString(tickers))
    }

    fun add(symbol: String, companyName: String?) {
        val upper = symbol.trim().uppercase()
        if (upper.isBlank() || _tickers.value.any { it.symbol == upper }) return
        val updated = _tickers.value + Ticker(upper, companyName?.takeIf { it.isNotBlank() })
        _tickers.value = updated
        persist(updated)
    }

    fun remove(symbol: String) {
        val updated = _tickers.value.filterNot { it.symbol == symbol }
        _tickers.value = updated
        persist(updated)
    }

    fun setMuted(symbol: String, muted: Boolean) {
        val updated = _tickers.value.map { if (it.symbol == symbol) it.copy(muted = muted) else it }
        _tickers.value = updated
        persist(updated)
    }
}
