package com.zoloft.innovv.app.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

class IosHaptics : Haptics {
    override fun perform(type: HapticType) {
        when (type) {
            HapticType.LIGHT -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
                .impactOccurred()
            HapticType.MEDIUM -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                .impactOccurred()
            HapticType.HEAVY -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
                .impactOccurred()
            HapticType.SUCCESS -> UINotificationFeedbackGenerator()
                .notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticType.WARNING -> UINotificationFeedbackGenerator()
                .notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
        }
    }
}

actual fun createHaptics(): Haptics = IosHaptics()
