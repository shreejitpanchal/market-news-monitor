package com.marketnewsmonitor.app.ui.widget

import com.marketnewsmonitor.app.data.local.entity.Ticker

data class WidgetRow(val symbol: String, val companyName: String?, val urgency: String?)

/** Pure, kept separate from [MarketNewsMonitorWidget.provideGlance] so it's testable without Glance/Android. */
fun buildWidgetRows(tickers: List<Ticker>, urgencyBySymbol: Map<String, String?>): List<WidgetRow> =
    tickers.map { WidgetRow(it.symbol, it.companyName, urgencyBySymbol[it.symbol]) }
