package com.marketnewsmonitor.app.data.notifications

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker

/** Extracted so poll orchestration ([com.marketnewsmonitor.app.work.NewsPollRunner]) is testable without a real Context. */
interface ArticleNotifier {
    /** Returns true if a notification was actually posted (false = nothing to show, or permission denied). */
    fun notifyNewArticles(ticker: Ticker, articles: List<Article>): Boolean
}
