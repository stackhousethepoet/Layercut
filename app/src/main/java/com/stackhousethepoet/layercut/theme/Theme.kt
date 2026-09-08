package com.stackhousethepoet.layercut.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple = Color(0xFF7C4DFF)
private val PurpleLight = Color(0xFFB388FF)
private val SurfaceDark = Color(0xFF1B1B1F)
private val SurfaceLight = Color(0xFFF5F5F7)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    secondary = Purple,
    background = SurfaceDark,
    surface = Color(0xFF2A2A2E),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = PurpleLight,
    background = SurfaceLight,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F)
)

@Composable
fun LayerCutTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
