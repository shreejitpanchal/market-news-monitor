package com.marketnewsmonitor.app.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchlistViewModel(private val tickerRepository: TickerRepository) : ViewModel() {

    private val query = MutableStateFlow("")

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

    fun addTicker(symbol: String, companyName: String?) {
        if (symbol.isBlank()) return
        viewModelScope.launch { tickerRepository.addTicker(symbol, companyName) }
    }

    fun removeTicker(ticker: Ticker) {
        viewModelScope.launch { tickerRepository.removeTicker(ticker) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                WatchlistViewModel(app.container.tickerRepository)
            }
        }
    }
}
