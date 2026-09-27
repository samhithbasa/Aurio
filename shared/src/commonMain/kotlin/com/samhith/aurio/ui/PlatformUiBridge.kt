package com.samhith.aurio.ui

enum class HapticFeedbackType {
    LIGHT_TICK,
    MEDIUM_TAP,
    HEAVY_CLICK,
    SUCCESS
}

/**
 * Cross-platform bridge for platform-specific micro-interactions:
 * - On iOS: Apple Taptic Engine (`UIImpactFeedbackGenerator`, `UINotificationFeedbackGenerator`)
 * - On Android: Android `Vibrator` / `HapticFeedbackConstants`
 */
expect object PlatformUiBridge {
    fun performHaptic(type: HapticFeedbackType)
    val isGlassmorphismSupported: Boolean
}
