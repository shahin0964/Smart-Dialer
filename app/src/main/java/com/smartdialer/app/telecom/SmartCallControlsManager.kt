package com.smartdialer.app.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.utils.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Manages smart hardware behaviors during incoming ringing calls:
 * 1. Flip-to-Silence (accelerometer face-down detection)
 * 2. Flashlight Blink Alert (CameraManager torch strobe)
 * 3. Caller Name Announcer (Android TextToSpeech)
 */
object SmartCallControlsManager {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var sensorManager: SensorManager? = null
    private var accelListener: SensorEventListener? = null
    private var sawFaceUp = false

    private var flashJob: Job? = null
    private var cameraManager: CameraManager? = null
    private var backCameraId: String? = null

    private var tts: TextToSpeech? = null
    private var ttsJob: Job? = null

    fun onIncomingRingingStarted(
        context: Context,
        displayName: String,
        rawNumber: String,
        isSilencedSpam: Boolean
    ) {
        val appContext = context.applicationContext
        val prefs = AppSettingsPreferences(appContext)
        if (isSilencedSpam) return

        if (prefs.flipToSilenceEnabled) {
            startFlipToSilence(appContext)
        }
        if (prefs.flashAlertEnabled) {
            startFlashAlert(appContext)
        }
        if (prefs.callerAnnouncerEnabled) {
            val spoken = if (displayName.isNotBlank() && displayName != rawNumber) {
                displayName
            } else {
                rawNumber.toCharArray().joinToString(" ")
            }
            if (spoken.isNotBlank()) {
                startCallerAnnouncer(appContext, spoken)
            }
        }
    }

    private fun startFlipToSilence(context: Context) {
        stopFlipToSilence()
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sensorManager = sm
            sawFaceUp = false

            accelListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val z = event?.values?.getOrNull(2) ?: return
                    if (z > 3.5f) {
                        sawFaceUp = true
                    } else if (sawFaceUp && z < -7.5f) {
                        sawFaceUp = false
                        silenceIncomingRinger(context)
                        stopFlipToSilence()
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(accelListener, accel, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun silenceIncomingRinger(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            stopFlashAlert()
            stopTts()
            VibrationHelper.vibrateCallConnected(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopFlipToSilence() {
        try {
            accelListener?.let { sensorManager?.unregisterListener(it) }
        } catch (_: Exception) {
        }
        accelListener = null
    }

    private fun startFlashAlert(context: Context) {
        stopFlashAlert()
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            cameraManager = cm
            val camId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lens = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lens == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return

            backCameraId = camId
            flashJob = scope.launch(Dispatchers.IO) {
                var torchOn = false
                try {
                    while (isActive) {
                        torchOn = !torchOn
                        cm.setTorchMode(camId, torchOn)
                        delay(if (torchOn) 220 else 380)
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        cm.setTorchMode(camId, false)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopFlashAlert() {
        flashJob?.cancel()
        flashJob = null
        try {
            backCameraId?.let { id -> cameraManager?.setTorchMode(id, false) }
        } catch (_: Exception) {
        }
    }

    private fun startCallerAnnouncer(context: Context, spokenName: String) {
        stopTts()
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    ttsJob = scope.launch {
                        delay(700)
                        tts?.speak(
                            "Incoming call from $spokenName",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "smart_dialer_caller_announcer"
                        )
                        delay(4500)
                        if (isActive) {
                            tts?.speak(
                                "Incoming call from $spokenName",
                                TextToSpeech.QUEUE_ADD,
                                null,
                                "smart_dialer_caller_announcer_repeat"
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTts() {
        ttsJob?.cancel()
        ttsJob = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    fun stopAll() {
        stopFlipToSilence()
        stopFlashAlert()
        stopTts()
    }
}
