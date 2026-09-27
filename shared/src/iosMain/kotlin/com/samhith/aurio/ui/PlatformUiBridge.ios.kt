package com.samhith.aurio.ui

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

actual object PlatformUiBridge {
    actual fun performHaptic(type: HapticFeedbackType) {
        when (type) {
            HapticFeedbackType.LIGHT_TICK -> {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
                generator.prepare()
                generator.impactOccurred()
            }
            HapticFeedbackType.MEDIUM_TAP -> {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                generator.prepare()
                generator.impactOccurred()
            }
            HapticFeedbackType.HEAVY_CLICK -> {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
                generator.prepare()
                generator.impactOccurred()
            }
            HapticFeedbackType.SUCCESS -> {
                val generator = UINotificationFeedbackGenerator()
                generator.prepare()
                generator.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            }
        }
    }

    actual val isGlassmorphismSupported: Boolean = true
}
