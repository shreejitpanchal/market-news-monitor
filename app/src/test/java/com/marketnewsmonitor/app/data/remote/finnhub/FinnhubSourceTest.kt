package com.marketnewsmonitor.app.data.remote.finnhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinnhubSourceTest {

    @Test
    fun `maps dto fields into an article keyed by source and url`() {
        val dtos = listOf(
            FinnhubNewsDto(headline = "Apple beats earnings", url = "https://example.com/a", source = "Reuters", datetime = 1_700_000_000L),
        )

        val articles = mapFinnhubNews("AAPL", dtos)

        assertEquals(1, articles.size)
        val article = articles.first()
        assertEquals("finnhub|https://example.com/a", article.id)
        assertEquals("AAPL", article.tickerSymbol)
        assertEquals("finnhub", article.sourceId)
        assertEquals("Apple beats earnings", article.headline)
        assertEquals(1_700_000_000_000L, article.publishedAt)
    }

    @Test
    fun `drops entries missing a headline or url`() {
        val dtos = listOf(
            FinnhubNewsDto(headline = "", url = "https://example.com/a"),
            FinnhubNewsDto(headline = "Valid headline", url = ""),
            FinnhubNewsDto(headline = "Valid headline", url = "https://example.com/b"),
        )

        val articles = mapFinnhubNews("AAPL", dtos)

        assertEquals(1, articles.size)
        assertTrue(articles.first().url == "https://example.com/b")
    }
}
