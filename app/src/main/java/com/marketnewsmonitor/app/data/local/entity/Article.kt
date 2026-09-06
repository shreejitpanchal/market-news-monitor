package com.marketnewsmonitor.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val id: String,
    val tickerSymbol: String,
    val sourceId: String,
    val headline: String,
    val url: String,
    val publishedAt: Long,
    val notified: Boolean = false,
    // Populated by Claude Haiku (Phase 4) — null until classified.
    val urgency: String? = null,
    val whyItMatters: String? = null,
    // Dedup clustering: articles sharing a non-null clusterId are the same
    // underlying story reported by different outlets. The value is one of
    // the group's own article ids (whichever was seen first), not a
    // separately generated id — see NewsRepository.classifyPending.
    val clusterId: String? = null,
)

/** Urgency values Claude is prompted to use — see ClaudeArticleClassifier. */
object Urgency {
    const val HOT = "hot"
    const val WARM = "warm"
    const val CALM = "calm"

    /** Higher first. Used to pick a ticker's headline badge among several articles. */
    val SEVERITY_ORDER = listOf(HOT, WARM, CALM)
}
