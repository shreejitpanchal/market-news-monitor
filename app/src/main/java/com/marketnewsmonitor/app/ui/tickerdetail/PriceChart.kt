package com.marketnewsmonitor.app.ui.tickerdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.marketnewsmonitor.app.data.remote.alphavantage.PricePoint
import java.text.DateFormat
import java.text.NumberFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/** No axes/gridlines/library — just a line through daily closes, plus a min/max/date-range caption. */
@Composable
fun PriceChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return

    val minClose = points.minOf { it.close }
    val maxClose = points.maxOf { it.close }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val range = (maxClose - minClose).takeIf { it > 0.0 } ?: 1.0
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = stepX * index
                val y = size.height - ((point.close - minClose) / range * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = LINE_COLOR, style = Stroke(width = 4f))
        }
        Text(
            "${currency.format(minClose)} – ${currency.format(maxClose)} · " +
                "${dateFormat.format(toDate(points.first()))} – ${dateFormat.format(toDate(points.last()))}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun toDate(point: PricePoint): Date =
    Date.from(point.date.atStartOfDay(ZoneOffset.UTC).toInstant())

private val LINE_COLOR = Color(0xFF1E88E5)
