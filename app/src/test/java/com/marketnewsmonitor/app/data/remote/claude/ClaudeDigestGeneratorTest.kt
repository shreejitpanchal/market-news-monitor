package com.marketnewsmonitor.app.data.remote.claude

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeDigestGeneratorTest {

    private fun article(headline: String, whyItMatters: String? = null) = Article(
        id = headline,
        tickerSymbol = "AAPL",
        sourceId = "finnhub",
        headline = headline,
        url = "https://example.com",
        publishedAt = 1L,
        whyItMatters = whyItMatters,
    )

    @Test
    fun `includes every ticker label and headline`() {
        val aapl = Ticker("AAPL", "Apple Inc.", 0L)
        val tsla = Ticker("TSLA", null, 0L)
        val prompt = buildDigestPrompt(
            mapOf(
                aapl to listOf(article("Apple beats earnings")),
                tsla to listOf(article("Tesla recalls vehicles")),
            ),
        )

        assertTrue(prompt.contains("AAPL (Apple Inc.)"))
        assertTrue(prompt.contains("Apple beats earnings"))
        assertTrue(prompt.contains("TSLA"))
        assertTrue(prompt.contains("Tesla recalls vehicles"))
    }

    @Test
    fun `includes why-it-matters when present`() {
        val ticker = Ticker("AAPL", "Apple Inc.", 0L)
        val prompt = buildDigestPrompt(mapOf(ticker to listOf(article("Apple beats earnings", "Guidance raised"))))

        assertTrue(prompt.contains("Guidance raised"))
    }

    @Test
    fun `asks for plain text suitable for a notification`() {
        val ticker = Ticker("AAPL", "Apple Inc.", 0L)
        val prompt = buildDigestPrompt(mapOf(ticker to listOf(article("Apple beats earnings"))))

        assertTrue(prompt.contains("no markdown"))
        assertFalse(prompt.contains("```"))
    }
}
