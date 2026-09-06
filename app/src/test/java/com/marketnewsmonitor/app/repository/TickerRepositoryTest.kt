package com.marketnewsmonitor.app.repository

import com.marketnewsmonitor.app.data.local.dao.TickerDao
import com.marketnewsmonitor.app.data.local.entity.Ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory fake — no Room/Robolectric needed to exercise repository logic. */
private class FakeTickerDao : TickerDao {
    private val state = MutableStateFlow<List<Ticker>>(emptyList())

    override fun observeAll(): Flow<List<Ticker>> = state
    override suspend fun getAll(): List<Ticker> = state.value

    override suspend fun upsert(ticker: Ticker) {
        state.value = state.value.filterNot { it.symbol == ticker.symbol } + ticker
    }

    override suspend fun delete(ticker: Ticker) {
        state.value = state.value.filterNot { it.symbol == ticker.symbol }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }

    override suspend fun setMuted(symbol: String, muted: Boolean) {
        state.value = state.value.map { if (it.symbol == symbol) it.copy(muted = muted) else it }
    }
}

class TickerRepositoryTest {

    @Test
    fun `addTicker normalizes symbol to trimmed uppercase`() = runBlocking {
        val repository = TickerRepository(FakeTickerDao())

        repository.addTicker("  aapl ", "Apple Inc.")

        val tickers = repository.getTickers()
        assertEquals(1, tickers.size)
        assertEquals("AAPL", tickers.first().symbol)
        assertEquals("Apple Inc.", tickers.first().companyName)
    }

    @Test
    fun `addTicker blanks out empty company name`() = runBlocking {
        val repository = TickerRepository(FakeTickerDao())

        repository.addTicker("TSLA", "   ")

        assertEquals(null, repository.getTickers().first().companyName)
    }

    @Test
    fun `replaceAll clears existing tickers before inserting`() = runBlocking {
        val repository = TickerRepository(FakeTickerDao())
        repository.addTicker("OLD", null)

        repository.replaceAll(listOf(Ticker("NEW", "New Co", 1L)))

        val tickers = repository.getTickers()
        assertEquals(listOf("NEW"), tickers.map { it.symbol })
    }

    @Test
    fun `removeTicker deletes only the matching symbol`() = runBlocking {
        val repository = TickerRepository(FakeTickerDao())
        repository.addTicker("AAPL", null)
        repository.addTicker("TSLA", null)

        repository.removeTicker(repository.getTickers().first { it.symbol == "AAPL" })

        assertEquals(listOf("TSLA"), repository.getTickers().map { it.symbol })
    }

    @Test
    fun `setMuted toggles only the matching ticker`() = runBlocking {
        val repository = TickerRepository(FakeTickerDao())
        repository.addTicker("AAPL", null)
        repository.addTicker("TSLA", null)

        repository.setMuted(repository.getTickers().first { it.symbol == "AAPL" }, muted = true)

        val tickers = repository.getTickers().associateBy { it.symbol }
        assertEquals(true, tickers.getValue("AAPL").muted)
        assertEquals(false, tickers.getValue("TSLA").muted)
    }
}
