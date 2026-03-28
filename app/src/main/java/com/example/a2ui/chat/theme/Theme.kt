package com.example.a2ui.chat.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    primary = Primary,
    primaryContainer = PrimaryContainer,
    secondaryContainer = SecondaryContainer
)

@Composable
fun A2UIChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
