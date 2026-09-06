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

    @Query("SELECT * FROM articles WHERE tickerSymbol = :symbol AND urgency IS NULL ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getUnclassified(symbol: String, limit: Int): List<Article>

    @Query("UPDATE articles SET urgency = :urgency, whyItMatters = :whyItMatters WHERE id = :id")
    suspend fun updateClassification(id: String, urgency: String, whyItMatters: String)

    /** Highest-severity urgency among a ticker's articles published inside the window, or null if none classified yet. */
    @Query(
        "SELECT urgency FROM articles WHERE tickerSymbol = :symbol AND urgency IS NOT NULL AND publishedAt >= :sinceMillis " +
            "ORDER BY CASE urgency WHEN 'hot' THEN 0 WHEN 'warm' THEN 1 WHEN 'calm' THEN 2 ELSE 3 END LIMIT 1",
    )
    suspend fun getLatestUrgency(symbol: String, sinceMillis: Long): String?

    /** Reactive, all-tickers version of [getLatestUrgency] — Dashboard badges update live as classification completes. */
    @Query("SELECT tickerSymbol, urgency FROM articles WHERE urgency IS NOT NULL AND publishedAt >= :sinceMillis")
    fun observeUrgenciesSince(sinceMillis: Long): Flow<List<TickerUrgency>>

    /**
     * Unlike [getUnnotifiedSince], not filtered by `notified` — the daily
     * digest re-summarizes anything notable in the window, even articles
     * already individually notified.
     */
    @Query("SELECT * FROM articles WHERE tickerSymbol = :symbol AND urgency IN (:urgencies) AND publishedAt >= :sinceMillis ORDER BY publishedAt DESC")
    suspend fun getRecentByUrgencies(symbol: String, sinceMillis: Long, urgencies: List<String>): List<Article>
}

data class TickerUrgency(val tickerSymbol: String, val urgency: String)
