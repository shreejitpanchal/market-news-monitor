package com.marketnewsmonitor.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(tickerRepository: TickerRepository) : ViewModel() {

    val tickers: StateFlow<List<Ticker>> = tickerRepository.observeTickers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                DashboardViewModel(app.container.tickerRepository)
            }
        }
    }
}
