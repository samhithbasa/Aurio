package com.samhith.aurio.ui

actual object PlatformUiBridge {
    actual fun performHaptic(type: HapticFeedbackType) {
        // Android handles haptics via Compose LocalHapticFeedback or MusicHapticsManager
    }

    actual val isGlassmorphismSupported: Boolean = false
}
