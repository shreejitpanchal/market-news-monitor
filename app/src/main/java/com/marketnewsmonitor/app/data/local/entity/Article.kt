package com.marketnewsmonitor.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Schema defined now per the Phase 0 roadmap; populated starting Phase 2 (live news feed). */
@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val id: String,
    val tickerSymbol: String,
    val sourceId: String,
    val headline: String,
    val url: String,
    val publishedAt: Long,
    val notified: Boolean = false,
)
