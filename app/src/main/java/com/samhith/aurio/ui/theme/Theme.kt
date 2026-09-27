package com.samhith.aurio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AurioColorScheme = lightColorScheme(
    primary = ClayPrimary,
    secondary = ClayPrimary,
    tertiary = ClayPrimaryLight,
    background = ClayBackground,
    surface = ClaySurface,
    surfaceVariant = ClayInset,
    outline = ClayInset,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ClayLabel,
    onSurface = ClayLabel,
    onSurfaceVariant = ClaySecondaryLabel,
    error = ClayPeach
)

/**
 * Aurio uses the Apple Modern light palette everywhere, regardless of the system dark-mode setting.
 */
@Composable
fun AurioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AurioColorScheme,
        typography = Typography,
        content = content
    )
}
