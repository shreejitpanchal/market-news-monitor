package com.marketnewsmonitor.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.marketnewsmonitor.app.data.local.entity.Article
import kotlinx.coroutines.flow.Flow

/** Unused until Phase 2 (live news feed) — schema defined now per the roadmap. */
@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE tickerSymbol = :symbol ORDER BY publishedAt DESC")
    fun observeForTicker(symbol: String): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<Article>)
}
