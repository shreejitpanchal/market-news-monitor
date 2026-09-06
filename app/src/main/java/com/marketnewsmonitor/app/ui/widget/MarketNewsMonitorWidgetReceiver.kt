package com.marketnewsmonitor.app.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The actual AppWidgetProvider Android's widget host binds to — registered in AndroidManifest.xml. */
class MarketNewsMonitorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MarketNewsMonitorWidget()
}
