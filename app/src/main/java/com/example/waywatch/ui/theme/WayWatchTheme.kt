package com.example.waywatch.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val PrimaryGreen = Color(0xFF1D9E75)
val SecondaryAmber = Color(0xFFEF9F27)
val BackgroundDark = Color(0xFF0A0F0D)
val SurfaceDark = Color(0xFF111A16)
val ErrorRed = Color(0xFFE53935)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryAmber,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorRed,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

@Composable
fun WayWatchTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
