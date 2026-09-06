package com.marketnewsmonitor.app.data.remote.rss

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.NewsSource
import java.io.IOException
import java.io.StringReader
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Replaces the Yahoo Finance/Reuters/MarketWatch RSS named in the original
 * architecture doc — those outlets no longer reliably serve public,
 * per-ticker RSS. Google News' per-query RSS is free, keyless, and stable.
 * See docs/ARCHITECTURE.md's data-source table for the full rationale.
 */
class GoogleNewsRssSource(private val client: OkHttpClient) : NewsSource {
    override val id = SOURCE_ID

    override suspend fun fetch(ticker: Ticker): List<Article> {
        val query = URLEncoder.encode("${ticker.symbol} stock", "UTF-8")
        val request = Request.Builder().url("$FEED_BASE?q=$query").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Google News RSS returned HTTP ${response.code} for ${ticker.symbol}")
            }
            val body = response.body?.string().orEmpty()
            return parseRssItems(body).map { it.toArticle(ticker.symbol) }
        }
    }

    companion object {
        const val SOURCE_ID = "google_news_rss"
        private const val FEED_BASE = "https://news.google.com/rss/search"
    }
}

data class RssItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedAtMillis: Long,
) {
    fun toArticle(symbol: String): Article = Article(
        id = "${GoogleNewsRssSource.SOURCE_ID}|$link",
        tickerSymbol = symbol,
        sourceId = GoogleNewsRssSource.SOURCE_ID,
        headline = title,
        url = link,
        publishedAt = publishedAtMillis,
    )
}

/**
 * Pure parsing, kept separate from [GoogleNewsRssSource.fetch] so it's
 * testable on a plain JVM without a network call or Robolectric — uses
 * javax.xml.parsers/org.w3c.dom (standard JDK, identical on Android) rather
 * than org.xmlpull.v1, which needs a real implementation on the classpath to
 * work outside an Android runtime.
 */
fun parseRssItems(xml: String): List<RssItem> {
    if (xml.isBlank()) return emptyList()

    val factory = DocumentBuilderFactory.newInstance().apply {
        // Disallowing DOCTYPE entirely is the standard XXE mitigation for
        // JAXP parsers — this feed is untrusted network content.
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    val itemNodes = document.getElementsByTagName("item")

    val items = mutableListOf<RssItem>()
    for (i in 0 until itemNodes.length) {
        val item = itemNodes.item(i) as? Element ?: continue
        val title = item.textOf("title")
        val link = item.textOf("link")
        if (title.isBlank() || link.isBlank()) continue
        val source = item.textOf("source")
        items += RssItem(
            title = title,
            link = link,
            source = source.ifBlank { title.substringAfterLast(" - ", "") },
            publishedAtMillis = parsePubDate(item.textOf("pubDate")),
        )
    }
    return items
}

private fun Element.textOf(tag: String): String =
    getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()

private fun parsePubDate(pubDate: String): Long =
    try {
        java.time.ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        0L
    }
