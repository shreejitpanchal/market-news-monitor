package com.marketnewsmonitor.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun UrgencyBadge(urgency: Urgency) {
    val color = when (urgency) {
        Urgency.HOT -> Color(0xFFD32F2F)
        Urgency.WARM -> Color(0xFFF9A825)
        Urgency.CALM -> Color(0xFF757575)
    }
    Text(
        urgency.label,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
