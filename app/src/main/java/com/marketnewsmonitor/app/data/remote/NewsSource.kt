package com.marketnewsmonitor.app.data.remote

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker

/**
 * One class per news source, looked up from [NewsSourceRegistry] — mirrors
 * coding-adventure's one-ExecutionEngine-per-language registry pattern.
 * Adding a data source later means adding one implementation, not branching
 * inside a shared fetcher.
 */
interface NewsSource {
    val id: String
    suspend fun fetch(ticker: Ticker): List<Article>
}
