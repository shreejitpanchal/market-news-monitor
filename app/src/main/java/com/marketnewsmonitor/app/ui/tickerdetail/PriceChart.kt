package com.marketnewsmonitor.app.ui.tickerdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketnewsmonitor.app.data.remote.alphavantage.PricePoint
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val LINE_COLOR = Color(0xFF1E88E5)
private val Y_AXIS_LABEL_WIDTH = 52.dp
private val X_AXIS_LABEL_HEIGHT = 18.dp
private val VOLUME_AREA_HEIGHT = 36.dp
private val VOLUME_GAP = 4.dp
private const val GRID_LINE_COUNT = 4

/**
 * A proper price chart, not just a bare line: gridlines + price labels on
 * the Y axis, date labels on the X axis, a filled area under the close
 * line, volume bars underneath (from the same Alpha Vantage response —
 * no extra API call), and a tap-to-inspect crosshair/tooltip.
 */
@Composable
fun PriceChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return

    val minClose = points.minOf { it.close }
    val maxClose = points.maxOf { it.close }
    val maxVolume = points.maxOf { it.volume }.coerceAtLeast(1L)
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val axisDateFormat = remember { SimpleDateFormat("MMM d", Locale.US) }
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val axisTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val volumeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val tooltipBackgroundColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val latest = points.last()
        Text(
            "${currency.format(latest.close)} · ${formatVolume(latest.volume)} vol · tap the chart for a point in time",
            style = MaterialTheme.typography.bodySmall,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(points) {
                    val plotWidth = size.width - Y_AXIS_LABEL_WIDTH.toPx()
                    if (plotWidth <= 0f || points.size < 2) return@pointerInput
                    val stepX = plotWidth / (points.size - 1)
                    detectTapGestures { offset ->
                        selectedIndex = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                    }
                },
        ) {
            val yAxisWidthPx = Y_AXIS_LABEL_WIDTH.toPx()
            val xAxisHeightPx = X_AXIS_LABEL_HEIGHT.toPx()
            val volumeHeightPx = VOLUME_AREA_HEIGHT.toPx()
            val volumeGapPx = VOLUME_GAP.toPx()

            val plotWidth = size.width - yAxisWidthPx
            val priceAreaHeight = size.height - xAxisHeightPx - volumeHeightPx - volumeGapPx
            val volumeTop = priceAreaHeight + volumeGapPx
            val range = (maxClose - minClose).takeIf { it > 0.0 } ?: 1.0
            val stepX = if (points.size > 1) plotWidth / (points.size - 1) else 0f

            // Y-axis gridlines + price labels.
            for (i in 0 until GRID_LINE_COUNT) {
                val fraction = i / (GRID_LINE_COUNT - 1).toFloat()
                val y = priceAreaHeight * fraction
                drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1.dp.toPx())
                val price = maxClose - (maxClose - minClose) * fraction
                val label = textMeasurer.measure(currency.format(price), TextStyle(fontSize = 10.sp, color = axisTextColor))
                val labelY = (y - label.size.height / 2f).coerceIn(0f, (priceAreaHeight - label.size.height).coerceAtLeast(0f))
                drawText(label, topLeft = Offset(plotWidth + 4.dp.toPx(), labelY))
            }

            // Close-price line + filled area under it.
            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { index, point ->
                val x = stepX * index
                val y = priceAreaHeight - ((point.close - minClose) / range * priceAreaHeight).toFloat()
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, priceAreaHeight)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(stepX * (points.size - 1), priceAreaHeight)
            fillPath.close()
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(LINE_COLOR.copy(alpha = 0.25f), Color.Transparent), endY = priceAreaHeight))
            drawPath(linePath, color = LINE_COLOR, style = Stroke(width = 3.dp.toPx()))

            // Volume bars, scaled to this series' own max — same data Alpha Vantage already returned.
            val barWidth = (plotWidth / points.size * 0.6f).coerceAtLeast(1f)
            points.forEachIndexed { index, point ->
                val x = stepX * index
                val barHeight = point.volume.toFloat() / maxVolume.toFloat() * volumeHeightPx
                drawRect(
                    color = volumeColor,
                    topLeft = Offset(x - barWidth / 2f, volumeTop + (volumeHeightPx - barHeight)),
                    size = Size(barWidth, barHeight),
                )
            }

            // X-axis date labels: first, middle, last point only, so they don't overlap.
            setOf(0, points.size / 2, points.size - 1).forEach { index ->
                val x = stepX * index
                val label = textMeasurer.measure(axisDateFormat.format(toDate(points[index])), TextStyle(fontSize = 10.sp, color = axisTextColor))
                val labelX = (x - label.size.width / 2f).coerceIn(0f, (plotWidth - label.size.width).coerceAtLeast(0f))
                drawText(label, topLeft = Offset(labelX, size.height - xAxisHeightPx + 2.dp.toPx()))
            }

            // Tap-to-inspect: a crosshair through the price area plus a floating tooltip.
            selectedIndex?.let { index ->
                val point = points[index]
                val x = stepX * index
                drawLine(axisTextColor, Offset(x, 0f), Offset(x, priceAreaHeight), strokeWidth = 1.dp.toPx())

                val tooltipText = "${axisDateFormat.format(toDate(point))}  ${currency.format(point.close)}  Vol ${formatVolume(point.volume)}"
                val tooltipLayout = textMeasurer.measure(tooltipText, TextStyle(fontSize = 11.sp, color = tooltipTextColor))
                val paddingPx = 6.dp.toPx()
                val boxWidth = tooltipLayout.size.width + paddingPx * 2
                val boxHeight = tooltipLayout.size.height + paddingPx * 2
                val boxX = (x - boxWidth / 2f).coerceIn(0f, (plotWidth - boxWidth).coerceAtLeast(0f))
                drawRoundRect(
                    color = tooltipBackgroundColor,
                    topLeft = Offset(boxX, 0f),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawText(tooltipLayout, topLeft = Offset(boxX + paddingPx, paddingPx))
            }
        }
    }
}

private fun toDate(point: PricePoint): Date =
    Date.from(point.date.atStartOfDay(ZoneOffset.UTC).toInstant())

private fun formatVolume(volume: Long): String = when {
    volume >= 1_000_000L -> "%.1fM".format(volume / 1_000_000.0)
    volume >= 1_000L -> "%.1fK".format(volume / 1_000.0)
    else -> volume.toString()
}
