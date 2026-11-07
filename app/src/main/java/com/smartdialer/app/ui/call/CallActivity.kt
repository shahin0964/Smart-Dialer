package com.smartdialer.app.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.lifecycleScope
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.telecom.FloatingCallHeadManager
import com.smartdialer.app.ui.theme.SmartDialerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Full-screen In-Call & Incoming Call Activity.
 * Implements OS-level proximity sensor blanking (PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
 * with Sensor.TYPE_PROXIMITY fallback, turning the display completely off when held to the ear
 * and turning it back on when removed, exactly like standard AOSP dialers.
 */
class CallActivity : ComponentActivity() {

    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var sensorEventListener: SensorEventListener? = null
    private val isNearEarState = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show when locked & turn screen on & keep screen on while visible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Hardware Proximity Screen-Off WakeLock
        initProximitySensor()

        // Observe call state changes
        lifecycleScope.launch {
            CallManager.currentCallState.collectLatest { state ->
                when {
                    state.status == CallStatus.DISCONNECTED -> {
                        releaseProximityLock()
                        finishAndRemoveTask()
                    }
                    state.isSpeakerOn -> {
                        // Speakerphone active: keep display on, do not blank screen
                        releaseProximityLock()
                    }
                    state.status == CallStatus.ACTIVE ||
                    state.status == CallStatus.RINGING ||
                    state.status == CallStatus.DIALING ||
                    state.status == CallStatus.CONNECTING -> {
                        // Handset earpiece active: enable proximity sensor screen off
                        acquireProximityLock()
                    }
                }
            }
        }

        setContent {
            val isNearEar by isNearEarState.collectAsState()

            SmartDialerTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OngoingCallScreen(
                        onDismiss = {
                            releaseProximityLock()
                            finishAndRemoveTask()
                        }
                    )

                    // Fallback visual & touch blocker if proximity wake lock is unsupported
                    if (isNearEar && proximityWakeLock == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .pointerInput(Unit) {
                                    // Consume all touch events to prevent cheek touches
                                }
                        )
                    }
                }
            }
        }
    }

    private fun initProximitySensor() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "SmartDialer:CallProximityWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Setup fallback sensor listener if hardware wake lock level is unsupported
        if (proximityWakeLock == null) {
            try {
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
                proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
                if (proximitySensor != null) {
                    sensorEventListener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            val distance = event?.values?.getOrNull(0) ?: return
                            val maxRange = proximitySensor?.maximumRange ?: 5f
                            val isNear = distance < maxRange && distance < 5f
                            isNearEarState.value = isNear

                            val params = window.attributes
                            if (isNear) {
                                params.screenBrightness = 0.001f
                            } else {
                                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            }
                            window.attributes = params
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun acquireProximityLock() {
        try {
            proximityWakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire()
                }
            }
            if (proximityWakeLock == null && sensorManager != null && proximitySensor != null && sensorEventListener != null) {
                sensorManager?.registerListener(
                    sensorEventListener,
                    proximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseProximityLock() {
        try {
            proximityWakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                }
            }
            if (proximityWakeLock == null) {
                sensorManager?.unregisterListener(sensorEventListener)
                isNearEarState.value = false
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        FloatingCallHeadManager.dismiss()
        val state = CallManager.currentCallState.value
        if (!state.isSpeakerOn && state.status != CallStatus.DISCONNECTED && state.status != CallStatus.IDLE) {
            acquireProximityLock()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User switched apps or pressed home; pause proximity blanking and show floating bubble
        releaseProximityLock()
        if (CallManager.currentCallState.value.status == CallStatus.ACTIVE) {
            FloatingCallHeadManager.show(this)
        }
    }

    override fun onDestroy() {
        releaseProximityLock()
        sensorManager?.unregisterListener(sensorEventListener)
        if (CallManager.currentCallState.value.status == CallStatus.DISCONNECTED ||
            CallManager.currentCallState.value.status == CallStatus.IDLE) {
            FloatingCallHeadManager.dismiss()
        }
        super.onDestroy()
    }
}
