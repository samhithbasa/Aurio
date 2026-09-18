package com.samhith.aurio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AurioColorScheme = lightColorScheme(
    primary = AppleBlue,
    secondary = AppleBlue,
    tertiary = AppleBlueLight,
    background = AppleBackground,
    surface = AppleSurface,
    surfaceVariant = AppleFill,
    outline = AppleSeparator,
    onPrimary = AppleOnAccent,
    onSecondary = AppleOnAccent,
    onTertiary = AppleOnAccent,
    onBackground = AppleLabel,
    onSurface = AppleLabel,
    onSurfaceVariant = AppleSecondaryLabel,
    error = AppleRed
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
