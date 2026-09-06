package com.marketnewsmonitor.app.ui.tickerdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RefreshUiState {
    data object Idle : RefreshUiState
    data object Refreshing : RefreshUiState
    data class Done(val message: String) : RefreshUiState
    data class Error(val message: String) : RefreshUiState
}

class TickerDetailViewModel(
    private val symbol: String,
    private val newsRepository: NewsRepository,
    tickerRepository: TickerRepository,
) : ViewModel() {

    val ticker: StateFlow<Ticker?> = tickerRepository.observeTickers()
        .map { tickers -> tickers.firstOrNull { it.symbol == symbol } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val articles: StateFlow<List<Article>> = newsRepository.observeArticles(symbol)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshState = MutableStateFlow<RefreshUiState>(RefreshUiState.Idle)
    val refreshState: StateFlow<RefreshUiState> = _refreshState.asStateFlow()

    fun refresh() {
        val currentTicker = ticker.value ?: Ticker(symbol, companyName = null, addedAt = 0L)
        _refreshState.value = RefreshUiState.Refreshing
        viewModelScope.launch {
            _refreshState.value = try {
                val result = newsRepository.refresh(currentTicker)
                if (result.failures.isEmpty()) {
                    RefreshUiState.Done("Fetched ${result.fetchedCount} new articles.")
                } else {
                    RefreshUiState.Done(
                        "Fetched ${result.fetchedCount} new articles. " +
                            "Some sources failed: ${result.failures.joinToString()}",
                    )
                }
            } catch (e: Exception) {
                RefreshUiState.Error("Refresh failed: ${e.message}")
            }
        }
    }

    companion object {
        fun factory(symbol: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                TickerDetailViewModel(symbol, app.container.newsRepository, app.container.tickerRepository)
            }
        }
    }
}
