package com.samhith.aurio.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Apple Modern palette - clean, minimal light UI
val AppleBackground = Color(0xFFF5F5F7)   // screen background
val AppleLabel = Color(0xFF1D1D1F)        // primary text and icons
val AppleGray = Color(0xFFAAAAAA)         // placeholders, inactive icons, hints
val AppleBlue = Color(0xFF007AFF)         // accent: buttons, selection, progress

// Supporting neutrals that complete the palette for real screens
val AppleSurface = Color(0xFFFFFFFF)      // cards, sheets, bars
val AppleFill = Color(0xFFE8E8ED)         // inputs, chips, tracks, pressed states
val AppleSeparator = Color(0xFFD2D2D7)    // borders and dividers

// Secondary text. #AAAAAA is too faint to read as body text on #F5F5F7 (about 2.2:1 contrast),
// so running text uses Apple's own secondary label grey and AppleGray stays for hints.
val AppleSecondaryLabel = Color(0xFF6E6E73)

val AppleBlueLight = Color(0xFF4DA3FF)    // softer accent for secondary highlights
val AppleOnAccent = Color(0xFFFFFFFF)     // text and icons drawn on AppleBlue or over artwork

// System status colors
val AppleGreen = Color(0xFF34C759)
val AppleRed = Color(0xFFFF3B30)

// Gradient Brushes. Apple buttons are flat, so the "gradient" is a barely-there blue shift.
val ApplePrimaryGradient = Brush.horizontalGradient(
    colors = listOf(AppleBlue, Color(0xFF0A84FF))
)
