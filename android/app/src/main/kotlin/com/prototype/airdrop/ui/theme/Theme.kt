package com.prototype.airdrop.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpaceDropDarkScheme = darkColorScheme(
    primary = SpaceDropColors.Primary,
    onPrimary = SpaceDropColors.OnSurface,
    primaryContainer = SpaceDropColors.SurfaceElevated,
    onPrimaryContainer = SpaceDropColors.OnSurface,
    secondary = SpaceDropColors.OnSurfaceVariant,
    onSecondary = SpaceDropColors.OnSurface,
    background = SpaceDropColors.Background,
    onBackground = SpaceDropColors.OnSurface,
    surface = SpaceDropColors.Surface,
    onSurface = SpaceDropColors.OnSurface,
    onSurfaceVariant = SpaceDropColors.OnSurfaceVariant,
    error = SpaceDropColors.Error,
    errorContainer = Color(0xFF3D2020),
    onError = SpaceDropColors.OnSurface,
    outline = SpaceDropColors.RingTrack,
    outlineVariant = SpaceDropColors.OrbitGuide
)

@Composable
fun PrototypeAirDropTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SpaceDropDarkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SpaceDropColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
