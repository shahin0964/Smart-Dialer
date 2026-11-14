package com.smartdialer.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Manages audio routing for VoIP / IP Calls across Bluetooth SCO headsets,
 * wired headsets, built-in earpiece, and speakerphone.
 */
class CallAudioRouteManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isSpeakerEnabled = false
    private var isAudioRoutingActive = false
    private var isBluetoothActive = false

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Callback invoked when audio route changes: (isSpeaker: Boolean, isBluetooth: Boolean)
    var onRouteChanged: ((Boolean, Boolean) -> Unit)? = null

    fun start() {
        if (isAudioRoutingActive) return
        isAudioRoutingActive = true
        isSpeakerEnabled = false
        isBluetoothActive = false

        requestCallAudioFocus()

        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            e.printStackTrace()
        }

        registerListeners()
        applyAudioRoute()
    }

    fun setSpeaker(enabled: Boolean) {
        isSpeakerEnabled = enabled
        applyAudioRoute()
    }

    fun isSpeakerOn(): Boolean = isSpeakerEnabled

    fun isBluetoothOn(): Boolean = isBluetoothActive

    fun isBluetoothConnected(): Boolean {
        val am = audioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.availableCommunicationDevices.any { isBluetoothDevice(it) } ||
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                am.isBluetoothScoAvailableOffCall || am.isBluetoothA2dpOn ||
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDevice(it) }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun applyAudioRoute() {
        val am = audioManager ?: return
        if (!isAudioRoutingActive) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSpeakerEnabled) {
                    am.clearCommunicationDevice()
                    val available = am.availableCommunicationDevices
                    val speaker = available.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        am.setCommunicationDevice(speaker)
                    } else {
                        am.isSpeakerphoneOn = true
                    }
                    isBluetoothActive = false
                    notifyRouteChanged(isSpeaker = true, isBluetooth = false)
                } else {
                    am.clearCommunicationDevice()
                    am.isSpeakerphoneOn = false
                    val available = am.availableCommunicationDevices

                    // 1. Look for Bluetooth Headset (SCO / BLE / A2DP / Hearing Aid)
                    val btDevice = available.firstOrNull { isBluetoothDevice(it) }
                    if (btDevice != null) {
                        val routed = am.setCommunicationDevice(btDevice)
                        isBluetoothActive = routed
                        notifyRouteChanged(isSpeaker = false, isBluetooth = isBluetoothActive)
                        if (!routed) {
                            // Retry after short delay for HAL transition
                            mainHandler.postDelayed({
                                if (!isSpeakerEnabled && isAudioRoutingActive) {
                                    val retryBt = am.availableCommunicationDevices.firstOrNull { isBluetoothDevice(it) }
                                    if (retryBt != null) {
                                        val retrySuccess = am.setCommunicationDevice(retryBt)
                                        isBluetoothActive = retrySuccess
                                        notifyRouteChanged(isSpeaker = false, isBluetooth = retrySuccess)
                                    }
                                }
                            }, 120)
                        }
                    } else {
                        // Check if Bluetooth is connected according to getDevices, and schedule retry if so
                        val hasBtConnected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDevice(it) }
                        if (hasBtConnected) {
                            mainHandler.postDelayed({
                                if (!isSpeakerEnabled && isAudioRoutingActive) {
                                    val retryBt = am.availableCommunicationDevices.firstOrNull { isBluetoothDevice(it) }
                                    if (retryBt != null) {
                                        val retrySuccess = am.setCommunicationDevice(retryBt)
                                        isBluetoothActive = retrySuccess
                                        notifyRouteChanged(isSpeaker = false, isBluetooth = retrySuccess)
                                    }
                                }
                            }, 150)
                        }

                        isBluetoothActive = false
                        // 2. Look for Wired Headset
                        val wired = available.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        }
                        if (wired != null) {
                            am.setCommunicationDevice(wired)
                        } else {
                            // 3. Fallback to Built-in Earpiece
                            val earpiece = available.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                            if (earpiece != null) {
                                am.setCommunicationDevice(earpiece)
                            } else {
                                am.clearCommunicationDevice()
                                am.isSpeakerphoneOn = false
                            }
                        }
                        notifyRouteChanged(isSpeaker = false, isBluetooth = false)
                    }
                }
            } else {
                // Android 11 and lower
                @Suppress("DEPRECATION")
                if (isSpeakerEnabled) {
                    am.stopBluetoothSco()
                    am.isBluetoothScoOn = false
                    am.isSpeakerphoneOn = true
                    isBluetoothActive = false
                    notifyRouteChanged(isSpeaker = true, isBluetooth = false)
                } else {
                    am.isSpeakerphoneOn = false
                    val hasBt = am.isBluetoothScoAvailableOffCall || am.isBluetoothA2dpOn ||
                            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDevice(it) }
                    if (hasBt) {
                        if (!am.isBluetoothScoOn) {
                            am.startBluetoothSco()
                            am.isBluetoothScoOn = true
                        }
                        isBluetoothActive = true
                        notifyRouteChanged(isSpeaker = false, isBluetooth = true)
                    } else {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        isBluetoothActive = false
                        notifyRouteChanged(isSpeaker = false, isBluetooth = false)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isBluetoothDevice(device: AudioDeviceInfo): Boolean {
        return device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && device.type == AudioDeviceInfo.TYPE_HEARING_AID) ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
    }

    private fun notifyRouteChanged(isSpeaker: Boolean, isBluetooth: Boolean) {
        mainHandler.post {
            onRouteChanged?.invoke(isSpeaker, isBluetooth)
        }
    }

    private fun requestCallAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { /* audio focus active */ }
                    .build()
                audioFocusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abandonCallAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerListeners() {
        val am = audioManager ?: return

        // 1. AudioDeviceCallback for dynamic connect / disconnect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    mainHandler.post { applyAudioRoute() }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    mainHandler.post { applyAudioRoute() }
                }
            }
            am.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        }

        // 2. BroadcastReceiver for Bluetooth SCO and Headset plug events
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED,
                    "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
                    Intent.ACTION_HEADSET_PLUG -> {
                        mainHandler.post { applyAudioRoute() }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction(Intent.ACTION_HEADSET_PLUG)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(bluetoothReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        if (!isAudioRoutingActive) return
        isAudioRoutingActive = false
        isSpeakerEnabled = false
        isBluetoothActive = false

        val am = audioManager
        if (am != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioDeviceCallback?.let { am.unregisterAudioDeviceCallback(it) }
                    am.clearCommunicationDevice()
                }
                @Suppress("DEPRECATION")
                am.stopBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = false
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        abandonCallAudioFocus()

        audioDeviceCallback = null
        bluetoothReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        bluetoothReceiver = null

        notifyRouteChanged(isSpeaker = false, isBluetooth = false)
    }
}
