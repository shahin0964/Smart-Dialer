package com.smartdialer.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.smartdialer.app.data.repository.AppSettingsPreferences

/**
 * Provides tactile vibration feedback for call connection and call disconnection.
 * Respects user preferences configured in Settings (callConnectedVibration, callEndedVibration).
 */
object VibrationHelper {

    fun vibrateCallConnected(context: Context) {
        val prefs = AppSettingsPreferences(context)
        if (!prefs.callConnectedVibration) return
        // 100ms crisp vibration when call is answered / connected
        vibrate(context, 100L)
    }

    fun vibrateCallEnded(context: Context) {
        val prefs = AppSettingsPreferences(context)
        if (!prefs.callEndedVibration) return
        // 150ms vibration when call disconnects / ends
        vibrate(context, 150L)
    }

    private fun vibrate(context: Context, durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vm?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
