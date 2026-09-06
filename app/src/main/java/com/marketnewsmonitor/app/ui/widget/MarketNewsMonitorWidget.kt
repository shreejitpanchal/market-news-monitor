package com.marketnewsmonitor.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.marketnewsmonitor.app.MainActivity
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.ui.theme.MarketAmber
import com.marketnewsmonitor.app.ui.theme.MarketGreen
import com.marketnewsmonitor.app.ui.theme.MarketRed
import java.util.concurrent.TimeUnit

val TickerSymbolKey = ActionParameters.Key<String>(MainActivity.EXTRA_TICKER_SYMBOL)

/** Mirrors the Dashboard: watchlist tickers with the same urgency badge, tap a row to deep-link into it. */
class MarketNewsMonitorWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as MarketNewsMonitorApp).container
        val tickers = container.tickerRepository.getTickers()
        val urgencyBySymbol = tickers.associate {
            it.symbol to container.newsRepository.getLatestUrgency(it.symbol, WINDOW_MILLIS)
        }
        val rows = buildWidgetRows(tickers, urgencyBySymbol)

        provideContent {
            if (rows.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(rows) { row -> WidgetRowContent(row) }
                }
            }
        }
    }

    companion object {
        private val WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24)
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text("No tickers yet — open the app to add one.")
    }
}

@Composable
private fun WidgetRowContent(row: WidgetRow) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(TickerSymbolKey to row.symbol))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(row.symbol, style = TextStyle(fontWeight = FontWeight.Bold))
            row.companyName?.let { Text(it, style = TextStyle(fontSize = 12.sp)) }
        }
        row.urgency?.let { urgency ->
            Box(
                modifier = GlanceModifier
                    .background(urgencyColor(urgency))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    urgency.replaceFirstChar { it.uppercase() },
                    style = TextStyle(color = whiteColorProvider(), fontSize = 10.sp),
                )
            }
        }
    }
}

// ColorProvider's factory requires both a day and a night color (Glance has
// no single-color overload) -- this widget doesn't need distinct light/dark
// art direction, so the same color is used for both.
private fun urgencyColor(urgency: String) = ColorProvider(
    day = urgencyBaseColor(urgency),
    night = urgencyBaseColor(urgency),
)

private fun urgencyBaseColor(urgency: String): Color = when (urgency) {
    Urgency.HOT -> MarketRed
    Urgency.WARM -> MarketAmber
    else -> MarketGreen
}

private fun whiteColorProvider() = ColorProvider(day = Color.White, night = Color.White)
