package com.marketnewsmonitor.app.data.remote.claude

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeArticleClassifierTest {

    private fun article(id: String, headline: String) = Article(
        id = id,
        tickerSymbol = "AAPL",
        sourceId = "finnhub",
        headline = headline,
        url = "https://example.com/$id",
        publishedAt = 1L,
    )

    @Test
    fun `prompt includes ticker label, urgency levels, and every article id and headline`() {
        val ticker = Ticker("AAPL", "Apple Inc.", 0L)
        val articles = listOf(article("a1", "Apple beats earnings"), article("a2", "Apple announces buyback"))

        val prompt = buildClassificationPrompt(ticker, articles)

        assertTrue(prompt.contains("AAPL (Apple Inc.)"))
        assertTrue(prompt.contains("\"hot\""))
        assertTrue(prompt.contains("\"warm\""))
        assertTrue(prompt.contains("\"calm\""))
        assertTrue(prompt.contains("\"a1\""))
        assertTrue(prompt.contains("Apple beats earnings"))
        assertTrue(prompt.contains("\"a2\""))
        assertTrue(prompt.contains("Apple announces buyback"))
    }

    @Test
    fun `prompt escapes a headline containing quotes`() {
        val ticker = Ticker("AAPL", null, 0L)
        val articles = listOf(article("a1", """Apple says "record quarter" in filing"""))

        val prompt = buildClassificationPrompt(ticker, articles)

        assertTrue(prompt.contains("\\\"record quarter\\\""))
    }

    @Test
    fun `falls back to just the symbol when there is no company name`() {
        val ticker = Ticker("AAPL", null, 0L)

        val prompt = buildClassificationPrompt(ticker, listOf(article("a1", "Headline")))

        assertTrue(prompt.contains("watching AAPL.") || prompt.contains("watching AAPL\n"))
    }

    @Test
    fun `parses a clean JSON array response`() {
        val text = """[{"id": "a1", "urgency": "hot", "why": "Earnings beat"}]"""

        val parsed = parseClassificationResponse(text)

        assertEquals(1, parsed.size)
        assertEquals("a1", parsed.first().id)
        assertEquals("hot", parsed.first().urgency)
        assertEquals("Earnings beat", parsed.first().why)
    }

    @Test
    fun `strips a markdown json fence before parsing`() {
        val text = "```json\n[{\"id\": \"a1\", \"urgency\": \"calm\", \"why\": \"Routine recap\"}]\n```"

        val parsed = parseClassificationResponse(text)

        assertEquals(1, parsed.size)
        assertEquals("calm", parsed.first().urgency)
    }

    @Test
    fun `returns an empty list for malformed json rather than throwing`() {
        val parsed = parseClassificationResponse("not json at all")

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `prompt instructs clustering of same-story articles`() {
        val ticker = Ticker("AAPL", "Apple Inc.", 0L)

        val prompt = buildClassificationPrompt(ticker, listOf(article("a1", "Headline")))

        assertTrue(prompt.contains("cluster"))
    }

    @Test
    fun `parses a cluster integer when present`() {
        val text = """[{"id": "a1", "urgency": "hot", "why": "Earnings beat", "cluster": 1}]"""

        val parsed = parseClassificationResponse(text)

        assertEquals(1, parsed.first().cluster)
    }

    @Test
    fun `defaults cluster to null when absent`() {
        val text = """[{"id": "a1", "urgency": "calm", "why": "Routine recap"}]"""

        val parsed = parseClassificationResponse(text)

        assertEquals(null, parsed.first().cluster)
    }
}
