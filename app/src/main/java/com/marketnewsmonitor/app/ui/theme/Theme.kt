package com.marketnewsmonitor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = MarketGreen,
    background = DarkBackground,
    surface = DarkSurface,
    error = MarketRed,
)

private val LightColors = lightColorScheme(
    primary = MarketGreen,
    error = MarketRed,
)

@Composable
fun MarketNewsMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
