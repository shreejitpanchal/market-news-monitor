package com.marketnewsmonitor.webapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Same visual approach as the real Android app's
 * `ui/tickerdetail/PriceChart.kt` (a hand-drawn Canvas line, no charting
 * library) — reimplemented here rather than shared, since this module
 * can't depend on the Android-only `app` module.
 */
@Composable
fun PriceChart(points: List<SamplePricePoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return

    val minClose = points.minOf { it.close }
    val maxClose = points.maxOf { it.close }

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
            drawPath(path, color = Color(0xFF1E88E5), style = Stroke(width = 4f))
        }
        Text(
            "${formatPrice(minClose)} – ${formatPrice(maxClose)} (sample data)",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// Avoids String.format (JVM-only in the Kotlin stdlib, not available on the wasmJs target).
private fun formatPrice(value: Double): String {
    val cents = kotlin.math.round(value * 100).toLong()
    val dollars = cents / 100
    val remainder = (cents % 100).let { if (it < 0) -it else it }
    val remainderText = if (remainder < 10) "0$remainder" else "$remainder"
    return "$$dollars.$remainderText"
}
