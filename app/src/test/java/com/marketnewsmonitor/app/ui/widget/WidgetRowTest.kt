package com.marketnewsmonitor.app.ui.widget

import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.local.entity.Urgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRowTest {

    @Test
    fun `pairs each ticker with its urgency by symbol`() {
        val tickers = listOf(Ticker("AAPL", "Apple Inc.", 0L), Ticker("TSLA", "Tesla", 0L))
        val urgencies = mapOf("AAPL" to Urgency.HOT, "TSLA" to null)

        val rows = buildWidgetRows(tickers, urgencies)

        assertEquals(2, rows.size)
        assertEquals(WidgetRow("AAPL", "Apple Inc.", Urgency.HOT), rows[0])
        assertEquals(WidgetRow("TSLA", "Tesla", null), rows[1])
    }

    @Test
    fun `a ticker missing from the urgency map gets a null urgency`() {
        val rows = buildWidgetRows(listOf(Ticker("AAPL", null, 0L)), emptyMap())

        assertNull(rows.single().urgency)
    }

    @Test
    fun `empty watchlist produces no rows`() {
        assertEquals(emptyList<WidgetRow>(), buildWidgetRows(emptyList(), emptyMap()))
    }
}
