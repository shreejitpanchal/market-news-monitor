package com.marketnewsmonitor.app.data.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.marketnewsmonitor.app.ui.widget.MarketNewsMonitorWidget

/** Extracted so [com.marketnewsmonitor.app.repository.NewsRepository] is testable without a real Context/Glance. */
interface WidgetUpdater {
    suspend fun requestUpdate()

    companion object {
        /** Default for NewsRepository's constructor, so existing 3-arg call sites (mostly tests) keep compiling. */
        val Noop = object : WidgetUpdater {
            override suspend fun requestUpdate() {}
        }
    }
}

/** Keeps the home-screen widget in sync with whatever NewsRepository.refresh() just wrote to Room. */
class HomeWidgetUpdater(private val context: Context) : WidgetUpdater {
    override suspend fun requestUpdate() {
        MarketNewsMonitorWidget().updateAll(context)
    }
}
