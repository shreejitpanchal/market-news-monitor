package com.marketnewsmonitor.app.data.remote.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNewsRssSourceTest {

    private val sampleFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>"AAPL stock" - Google News</title>
            <item>
              <title>Apple stock jumps on earnings - Example Wire</title>
              <link>https://example.com/apple-earnings</link>
              <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
              <source url="https://example.com">Example Wire</source>
            </item>
            <item>
              <title>Untitled fallback source parsing - Some Publisher</title>
              <link>https://example.com/no-source-tag</link>
              <pubDate>Tue, 02 Jan 2024 09:30:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `parses items with title link source and pubDate`() {
        val items = parseRssItems(sampleFeed)

        assertEquals(2, items.size)
        val first = items.first()
        assertEquals("Apple stock jumps on earnings - Example Wire", first.title)
        assertEquals("https://example.com/apple-earnings", first.link)
        assertEquals("Example Wire", first.source)
        assertTrue(first.publishedAtMillis > 0L)
    }

    @Test
    fun `falls back to the title suffix when a source tag is missing`() {
        val items = parseRssItems(sampleFeed)

        val second = items.last()
        assertEquals("Some Publisher", second.source)
    }

    @Test
    fun `returns empty list for blank input`() {
        assertEquals(emptyList<RssItem>(), parseRssItems(""))
    }

    @Test
    fun `toArticle builds a stable id from source and link`() {
        val item = RssItem(title = "Headline", link = "https://example.com/x", source = "Wire", publishedAtMillis = 1L)

        val article = item.toArticle("AAPL")

        assertEquals("google_news_rss|https://example.com/x", article.id)
        assertEquals("google_news_rss", article.sourceId)
        assertEquals("AAPL", article.tickerSymbol)
    }
}
