package com.marketnewsmonitor.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.finnhub.TickerSuggestion
import com.marketnewsmonitor.app.data.remote.finnhub.TickerSymbolSearch
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchlistViewModel(
    private val tickerRepository: TickerRepository,
    private val tickerSymbolSearch: TickerSymbolSearch,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _tickerSuggestions = MutableStateFlow<List<TickerSuggestion>>(emptyList())
    val tickerSuggestions: StateFlow<List<TickerSuggestion>> = _tickerSuggestions.asStateFlow()
    private var suggestionSearchJob: Job? = null

    val tickers: StateFlow<List<Ticker>> =
        combine(tickerRepository.observeTickers(), query) { tickers, q ->
            if (q.isBlank()) {
                tickers
            } else {
                tickers.filter {
                    it.symbol.contains(q, ignoreCase = true) ||
                        it.companyName?.contains(q, ignoreCase = true) == true
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** Debounced so every keystroke in the Add Ticker dialog doesn't fire its own network call. */
    fun onAddTickerSymbolChange(value: String) {
        suggestionSearchJob?.cancel()
        if (value.isBlank()) {
            _tickerSuggestions.value = emptyList()
            return
        }
        suggestionSearchJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MILLIS)
            _tickerSuggestions.value = tickerSymbolSearch.search(value)
        }
    }

    fun clearTickerSuggestions() {
        suggestionSearchJob?.cancel()
        _tickerSuggestions.value = emptyList()
    }

    fun addTicker(symbol: String, companyName: String?) {
        if (symbol.isBlank()) return
        viewModelScope.launch { tickerRepository.addTicker(symbol, companyName) }
    }

    fun removeTicker(ticker: Ticker) {
        viewModelScope.launch { tickerRepository.removeTicker(ticker) }
    }

    fun toggleMuted(ticker: Ticker) {
        viewModelScope.launch { tickerRepository.setMuted(ticker, !ticker.muted) }
    }

    companion object {
        private const val SUGGESTION_DEBOUNCE_MILLIS = 300L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                WatchlistViewModel(app.container.tickerRepository, app.container.tickerSymbolSearch)
            }
        }
    }
}
