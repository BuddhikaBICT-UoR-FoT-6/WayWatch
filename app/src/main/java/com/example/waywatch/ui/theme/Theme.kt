package com.example.waywatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/** Alias kept for backward compatibility — delegates to WayWatchTheme. */
@Composable
fun waywatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = WayWatchTheme(content = content)
