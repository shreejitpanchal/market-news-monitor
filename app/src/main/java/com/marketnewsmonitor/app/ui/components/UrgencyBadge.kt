package com.marketnewsmonitor.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marketnewsmonitor.app.data.local.entity.Urgency
import com.marketnewsmonitor.app.ui.theme.MarketAmber
import com.marketnewsmonitor.app.ui.theme.MarketGreen
import com.marketnewsmonitor.app.ui.theme.MarketRed

/** Shared between Dashboard (per-ticker) and ticker detail (per-article). Renders nothing for an unrecognized/null urgency. */
@Composable
fun UrgencyBadge(urgency: String?, modifier: Modifier = Modifier) {
    val (color, label) = when (urgency) {
        Urgency.HOT -> MarketRed to "Hot"
        Urgency.WARM -> MarketAmber to "Warm"
        Urgency.CALM -> MarketGreen to "Calm"
        else -> return
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
