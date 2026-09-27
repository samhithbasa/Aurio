package com.samhith.aurio.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ===================================================================================
// Claymorphism Palette — Soft, Inflated, 3D Tactile Aesthetic
// 1. #F0EFF6 — Soft Warm / Lavender-tinted Pastel Canvas
// 2. #2C2C38 — Soft Deep Charcoal Primary Text & High Contrast Elements
// 3. #7A96FE / #6C5CE7 — Signature Periwinkle / Sky Clay Accent
// 4. Pastel Clay Accents: Peach (#FFB2B2), Mint (#B0ECC8), Lilac (#D4CCFC)
// ===================================================================================

val ClayBackground = Color(0xFFF0EFF6)       // Soft lavender-tinted off-white clay canvas
val ClaySurface = Color(0xFFFFFFFF)          // Pure white 3D clay surface
val ClaySurfaceSoft = Color(0xFFF6F5FB)      // Soft white clay gradient base
val ClayCard = Color(0xFFFFFFFF)             // Clay card background surface color
val ClayLabel = Color(0xFF2C2C38)            // Primary deep charcoal text
val ClaySecondaryLabel = Color(0xFF8A8A9E)   // Subtitles, hints, and secondary text
val ClayInactiveIcon = Color(0xFF9494A8)     // Inactive navigation & icon tint

// Signature Clay Primary Colors
val ClayPrimary = Color(0xFF7A96FE)          // Vibrant soft periwinkle blue clay
val ClayPrimaryLight = Color(0xFF9FB6FF)     // Light highlight gradient
val ClayPrimaryDark = Color(0xFF5A74E8)      // Deep shadow gradient
val ClayPrimaryGlow = Color(0x357A96FE)      // Ambient colored glow

// Pastel Clay Category Accents
val ClayPeach = Color(0xFFFFB8B8)            // Liked songs, hearts, favorites
val ClayPeachDark = Color(0xFFFA9292)
val ClayPeachLight = Color(0xFFFFD4D4)

val ClayMint = Color(0xFFB0ECC8)             // Spotify import, success
val ClayMintDark = Color(0xFF7CDAA2)
val ClayMintLight = Color(0xFFD6F8E4)

val ClayLilac = Color(0xFFD4CCFC)            // Downloads, rooms, playlists
val ClayLilacDark = Color(0xFFB0A2F8)
val ClayLilacLight = Color(0xFFEBE7FE)

val ClayYellow = Color(0xFFFFE082)           // Stars, highlights
val ClayYellowDark = Color(0xFFFFCA28)

// Inset Trough / Input Background
val ClayInset = Color(0xFFE6E4EE)            // Debossed clay input & search trough
val ClayInsetBorder = Color(0xFFD8D5E4)

// Shadow & Highlight Colors
val ClayShadowDark = Color(0x288C90AB)       // Soft bottom-right diffuse shadow
val ClayShadowAmbient = Color(0x14707490)    // Ambient surrounding shadow
val ClayHighlight = Color(0xFFFFFFFF)        // Top-left specular highlight

// Backwards-compatible Apple/Metallic Aliases so all components inherit Clay Theme
val AppleBackground = ClayBackground
val AppleLabel = ClayLabel
val AppleSecondaryLabel = ClaySecondaryLabel
val AppleMain = ClayPrimary
val AppleGray = ClaySecondaryLabel
val AppleSilver = ClayPrimary
val AppleSilverLight = ClayPrimaryLight
val AppleSilverDark = ClayPrimaryDark
val AppleSilverUltraLight = ClaySurfaceSoft
val AppleBlue = ClayPrimary
val AppleBlueLight = ClayPrimaryLight
val AppleOnAccent = Color.White
val AppleSurface = ClaySurface
val AppleSurfaceGlass = Color(0xF4FFFFFF)
val AppleFill = ClayInset
val AppleSeparator = Color(0xFFDCD9E8)
val AppleGreen = ClayMintDark
val AppleRed = Color(0xFFFF5252)

// ===================================================================================
// Claymorphic 3D Gradients & Light Brushes
// ===================================================================================

// Primary Periwinkle Clay Gradient (for active pills, floating bubble, CTA buttons)
val ClayPrimaryGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF9FB6FF),
        Color(0xFF7A96FE),
        Color(0xFF5E79EB)
    )
)

// Soft White Raised Clay Gradient (for cards, unselected pills, containers)
val ClayCardGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFF6F5FB),
        Color(0xFFECEAF4)
    )
)

// Inset Clay Trough Gradient (for search bar, inputs, debossed slots)
val ClayInsetGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFE2E0EB),
        Color(0xFFECEAF4),
        Color(0xFFF4F3F9)
    )
)

// Pastel Peach Clay Gradient (for Liked Songs, Hearts)
val ClayPeachGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFD4D4),
        Color(0xFFFFB8B8),
        Color(0xFFFA9292)
    )
)

// Pastel Mint Clay Gradient (for Spotify import)
val ClayMintGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFD6F8E4),
        Color(0xFFB0ECC8),
        Color(0xFF86E0A8)
    )
)

// Pastel Lilac Clay Gradient (for Downloads, Room bubbles)
val ClayLilacGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFEBE7FE),
        Color(0xFFD4CCFC),
        Color(0xFFB5A7F8)
    )
)

// Aliased Apple Gradients to Clay Gradients
val ApplePrimaryGradient = ClayPrimaryGradient
val AppleSilverGlossGradient = ClayPrimaryGradient
val AppleWhiteCardGradient = ClayCardGradient
val AppleDarkPillGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2C2C38),
        Color(0xFF1E1E28),
        Color(0xFF14141E)
    )
)
val AppleGlassShine = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.85f),
        Color.White.copy(alpha = 0.25f),
        Color.Transparent
    )
)
val AppleCardBorderGradient = Brush.verticalGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.95f),
        Color(0xFFE0DDEB).copy(alpha = 0.60f),
        Color(0xFFC8C4D8).copy(alpha = 0.30f)
    )
)
