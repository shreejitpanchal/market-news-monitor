package com.marketnewsmonitor.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marketnewsmonitor.app.data.local.entity.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE tickerSymbol = :symbol ORDER BY publishedAt DESC")
    fun observeForTicker(symbol: String): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<Article>)

    /**
     * Notify-eligible articles: not yet notified, and published inside the
     * freshness window — bounds a freshly-added ticker's first poll from
     * dumping a week of backfill as one giant "new" notification. Backfill
     * articles still get inserted and are visible in ticker detail; they
     * just never notify.
     */
    @Query("SELECT * FROM articles WHERE tickerSymbol = :symbol AND notified = 0 AND publishedAt >= :sinceMillis ORDER BY publishedAt DESC")
    suspend fun getUnnotifiedSince(symbol: String, sinceMillis: Long): List<Article>

    @Query("UPDATE articles SET notified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>)
}
