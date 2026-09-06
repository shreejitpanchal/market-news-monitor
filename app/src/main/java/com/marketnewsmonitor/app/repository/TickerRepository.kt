package com.marketnewsmonitor.app.repository

import com.marketnewsmonitor.app.data.local.dao.TickerDao
import com.marketnewsmonitor.app.data.local.entity.Ticker
import kotlinx.coroutines.flow.Flow

class TickerRepository(private val tickerDao: TickerDao) {

    fun observeTickers(): Flow<List<Ticker>> = tickerDao.observeAll()

    suspend fun getTickers(): List<Ticker> = tickerDao.getAll()

    suspend fun addTicker(symbol: String, companyName: String?) {
        tickerDao.upsert(
            Ticker(
                symbol = symbol.trim().uppercase(),
                companyName = companyName?.trim()?.takeIf { it.isNotEmpty() },
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun removeTicker(ticker: Ticker) {
        tickerDao.delete(ticker)
    }

    suspend fun replaceAll(tickers: List<Ticker>) {
        tickerDao.deleteAll()
        tickers.forEach { tickerDao.upsert(it) }
    }

    suspend fun setMuted(ticker: Ticker, muted: Boolean) {
        tickerDao.setMuted(ticker.symbol, muted)
    }
}
