package com.smartdialer.app.telecom

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import com.smartdialer.app.data.model.ActiveCallInfo
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.ui.call.CallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object CallManager {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var durationJob: Job? = null

    private val _currentCallState = MutableStateFlow(ActiveCallInfo())
    val currentCallState: StateFlow<ActiveCallInfo> = _currentCallState.asStateFlow()

    private var activeCall: Call? = null
    private var inCallService: CallService? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateFromCall(call)
        }
    }

    fun updateAudioState(audioState: CallAudioState) {
        _currentCallState.value = _currentCallState.value.copy(
            isMuted = audioState.isMuted,
            isSpeakerOn = (audioState.route == CallAudioState.ROUTE_SPEAKER)
        )
    }

    fun registerInCallService(service: CallService) {
        inCallService = service
    }

    fun unregisterInCallService() {
        inCallService = null
    }

    fun setCall(call: Call, context: Context) {
        activeCall?.unregisterCallback(callCallback)
        activeCall = call
        call.registerCallback(callCallback)
        updateFromCall(call)

        // Launch CallActivity
        val intent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    fun clearCall() {
        activeCall?.unregisterCallback(callCallback)
        activeCall = null
        stopDurationTimer()
        _currentCallState.value = ActiveCallInfo(status = CallStatus.DISCONNECTED)
    }

    private fun updateFromCall(call: Call) {
        val handle = call.details?.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""
        val callerName = call.details?.callerDisplayName ?: ""

        val status = when (call.state) {
            Call.STATE_DIALING -> CallStatus.DIALING
            Call.STATE_RINGING -> CallStatus.RINGING
            Call.STATE_ACTIVE -> CallStatus.ACTIVE
            Call.STATE_HOLDING -> CallStatus.HOLDING
            Call.STATE_CONNECTING -> CallStatus.CONNECTING
            Call.STATE_DISCONNECTED -> CallStatus.DISCONNECTED
            else -> CallStatus.IDLE
        }

        val audioState = inCallService?.callAudioState
        val isMuted = audioState?.isMuted ?: false
        val isSpeaker = (audioState?.route == CallAudioState.ROUTE_SPEAKER)

        if (status == CallStatus.ACTIVE && durationJob == null) {
            startDurationTimer()
        } else if (status != CallStatus.ACTIVE && status != CallStatus.HOLDING) {
            stopDurationTimer()
        }

        _currentCallState.value = _currentCallState.value.copy(
            callId = call.toString(),
            number = rawNumber,
            contactName = callerName,
            status = status,
            isMuted = isMuted,
            isSpeakerOn = isSpeaker,
            isHolding = (status == CallStatus.HOLDING)
        )
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                _currentCallState.value = _currentCallState.value.copy(durationSeconds = seconds)
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    fun answer() {
        activeCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activeCall?.reject(Call.REJECT_REASON_DECLINED)
        } else {
            @Suppress("DEPRECATION")
            activeCall?.reject(false, null as? String)
        }
        clearCall()
    }

    fun disconnect() {
        activeCall?.disconnect()
        clearCall()
    }

    fun toggleMute() {
        val currentMute = _currentCallState.value.isMuted
        inCallService?.setMuted(!currentMute)
        _currentCallState.value = _currentCallState.value.copy(isMuted = !currentMute)
    }

    fun toggleSpeaker(context: Context) {
        val currentSpeaker = _currentCallState.value.isSpeakerOn
        val targetRoute = if (currentSpeaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        inCallService?.setAudioRoute(targetRoute)
        _currentCallState.value = _currentCallState.value.copy(isSpeakerOn = !currentSpeaker)
    }

    fun toggleHold() {
        val call = activeCall ?: return
        if (call.state == Call.STATE_HOLDING) {
            call.unhold()
        } else {
            call.hold()
        }
    }

    fun toggleRecording() {
        val current = _currentCallState.value.isRecording
        _currentCallState.value = _currentCallState.value.copy(isRecording = !current)
    }

    fun sendDtmf(digit: Char) {
        activeCall?.playDtmfTone(digit)
        scope.launch {
            delay(200)
            activeCall?.stopDtmfTone()
        }
    }

    fun initiateCall(context: Context, number: String, simSlot: Int = 0) {
        val cleanNumber = number.replace("#", "%23")
        val uri = Uri.parse("tel:$cleanNumber")
        val telecomManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelecomManager

        try {
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                // Attempt to specify SIM slot extra
                putExtra("com.android.phone.extra.slot", simSlot)
                putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", simSlot)
                putExtra("simSlot", simSlot)
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            // Fallback to ACTION_DIAL if permission missing
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
