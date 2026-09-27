package com.samhith.aurio.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies 3D Claymorphic Card styling.
 */
fun Modifier.appleGlossyCard(
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 8.dp,
    borderAlpha: Float = 0.90f
): Modifier = this.clayCard(
    cornerRadius = cornerRadius,
    elevation = elevation,
    gradient = ClayCardGradient,
    borderAlpha = borderAlpha
)

/**
 * Applies 3D Claymorphic Pill styling.
 */
fun Modifier.appleGlossyPill(
    isSelected: Boolean,
    cornerRadius: Dp = 100.dp
): Modifier = this.clayPill(
    isSelected = isSelected,
    cornerRadius = cornerRadius,
    selectedGradient = ClayPrimaryGradient,
    unselectedGradient = ClayCardGradient
)

/**
 * Tactile 3D Claymorphic Button styling.
 */
fun Modifier.appleGlossyButton(
    brush: Brush = ClayPrimaryGradient,
    cornerRadius: Dp = 100.dp,
    elevation: Dp = 7.dp
): Modifier = this.clayButton(
    gradient = brush,
    cornerRadius = cornerRadius,
    elevation = elevation
)

/**
 * Soft Ambient Colored Glow for Clay elements.
 */
fun Modifier.ClayPrimaryGlow(
    alpha: Float = 0.30f,
    blurRadius: Dp = 16.dp,
    offsetY: Dp = 4.dp
): Modifier = this.clayGlow(
    color = ClayPrimary,
    alpha = alpha,
    blurRadius = blurRadius,
    offsetY = offsetY
)
