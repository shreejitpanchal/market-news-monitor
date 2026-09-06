package com.marketnewsmonitor.app.data.remote

class NewsSourceRegistry(private val sources: List<NewsSource>) {
    fun all(): List<NewsSource> = sources
}
