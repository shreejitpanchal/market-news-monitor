package com.marketnewsmonitor.app.ui.tickerdetail

import com.marketnewsmonitor.app.data.local.entity.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleGroupTest {

    private fun article(id: String, sourceId: String, publishedAt: Long, clusterId: String? = null) = Article(
        id = id,
        tickerSymbol = "AAPL",
        sourceId = sourceId,
        headline = "Headline $id",
        url = "https://example.com/$id",
        publishedAt = publishedAt,
        clusterId = clusterId,
    )

    @Test
    fun `an unclustered article is its own singleton group`() {
        val groups = groupArticlesForDisplay(listOf(article("a1", "finnhub", 1L)))

        assertEquals(1, groups.size)
        assertEquals("a1", groups.single().primary.id)
        assertTrue(groups.single().alsoReportedBy.isEmpty())
    }

    @Test
    fun `clustered articles collapse into one group with the earliest as primary`() {
        val earlier = article("a1", "finnhub", publishedAt = 100L, clusterId = "a1")
        val later = article("a2", "google_news_rss", publishedAt = 200L, clusterId = "a1")

        val groups = groupArticlesForDisplay(listOf(later, earlier))

        assertEquals(1, groups.size)
        assertEquals("a1", groups.single().primary.id)
        assertEquals(listOf("a2"), groups.single().alsoReportedBy.map { it.id })
    }

    @Test
    fun `groups are ordered by their most recent member`() {
        val staleGroup = article("old", "finnhub", publishedAt = 1L)
        val freshGroup = article("new", "finnhub", publishedAt = 2L)

        val groups = groupArticlesForDisplay(listOf(staleGroup, freshGroup))

        assertEquals(listOf("new", "old"), groups.map { it.primary.id })
    }

    @Test
    fun `two unrelated articles with different cluster ids stay separate`() {
        val groups = groupArticlesForDisplay(
            listOf(
                article("a1", "finnhub", 1L, clusterId = "a1"),
                article("b1", "finnhub", 2L, clusterId = "b1"),
            ),
        )

        assertEquals(2, groups.size)
    }
}
