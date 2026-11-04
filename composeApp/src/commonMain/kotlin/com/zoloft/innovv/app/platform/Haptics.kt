package com.zoloft.innovv.app.platform

enum class HapticType { LIGHT, MEDIUM, HEAVY, SUCCESS, WARNING }

interface Haptics {
    fun perform(type: HapticType)
}

expect fun createHaptics(): Haptics
