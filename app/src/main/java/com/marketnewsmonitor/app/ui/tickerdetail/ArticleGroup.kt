package com.marketnewsmonitor.app.ui.tickerdetail

import com.marketnewsmonitor.app.data.local.entity.Article

data class ArticleGroup(val primary: Article, val alsoReportedBy: List<Article>)

/**
 * Pure, kept separate from the screen so it's testable without Compose.
 * Groups by [Article.clusterId] — each null-clusterId article is its own
 * singleton group. Primary = earliest publishedAt in the group ("who
 * reported it first"); groups are ordered by their most recent member so an
 * updated story still bubbles to the top, matching the existing
 * publishedAt-DESC feed ordering.
 */
fun groupArticlesForDisplay(articles: List<Article>): List<ArticleGroup> =
    articles.groupBy { it.clusterId ?: it.id }
        .map { (_, members) ->
            val sorted = members.sortedBy { it.publishedAt }
            ArticleGroup(primary = sorted.first(), alsoReportedBy = sorted.drop(1))
        }
        .sortedByDescending { group -> (listOf(group.primary) + group.alsoReportedBy).maxOf { it.publishedAt } }
