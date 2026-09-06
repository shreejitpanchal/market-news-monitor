package com.marketnewsmonitor.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

class DashboardViewModel(tickerRepository: TickerRepository, newsRepository: NewsRepository) : ViewModel() {

    val tickers: StateFlow<List<Ticker>> = tickerRepository.observeTickers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Symbol -> highest-severity urgency among its recent articles, for each card's badge. */
    val urgencyByTicker: StateFlow<Map<String, String>> = newsRepository.observeUrgencies(URGENCY_WINDOW_MILLIS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        private val URGENCY_WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                DashboardViewModel(app.container.tickerRepository, app.container.newsRepository)
            }
        }
    }
}
