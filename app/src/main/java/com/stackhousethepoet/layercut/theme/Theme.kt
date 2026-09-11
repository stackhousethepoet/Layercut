package com.stackhousethepoet.layercut.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Neon cyan-teal accent — high-energy electric blue-green. */
private val ElectricCyan = Color(0xFF00E8C8)
private val ElectricCyanBright = Color(0xFF6FFFF0)
private val ElectricCyanDeep = Color(0xFF00BFA5)
private val OnAccent = Color(0xFF003830)
private val SurfaceDark = Color(0xFF1B1B1F)
private val SurfaceLight = Color(0xFFF5F5F7)

private val DarkColors = darkColorScheme(
    primary = ElectricCyanBright,
    secondary = ElectricCyan,
    tertiary = ElectricCyanDeep,
    background = SurfaceDark,
    surface = Color(0xFF2A2A2E),
    onPrimary = OnAccent,
    onSecondary = OnAccent,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColors = lightColorScheme(
    primary = ElectricCyanDeep,
    secondary = ElectricCyan,
    tertiary = ElectricCyanBright,
    background = SurfaceLight,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = OnAccent,
    onTertiary = OnAccent,
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
