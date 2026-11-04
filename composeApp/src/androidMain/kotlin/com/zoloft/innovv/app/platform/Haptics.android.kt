package com.zoloft.innovv.app.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AndroidHaptics : Haptics {
    private val context: Context = AndroidPlatformContext.application.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun perform(type: HapticType) {
        val duration = when (type) {
            HapticType.LIGHT -> 18L
            HapticType.MEDIUM -> 35L
            HapticType.HEAVY -> 60L
            HapticType.SUCCESS -> 30L
            HapticType.WARNING -> 50L
        }
        val amplitude = when (type) {
            HapticType.LIGHT -> 70
            HapticType.MEDIUM -> 140
            HapticType.HEAVY -> 255
            HapticType.SUCCESS -> 120
            HapticType.WARNING -> 200
        }
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }
}

actual fun createHaptics(): Haptics = AndroidHaptics()
