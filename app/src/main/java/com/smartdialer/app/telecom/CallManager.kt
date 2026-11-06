package com.smartdialer.app.telecom

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.ActiveCallInfo
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.model.SpamAction
import com.smartdialer.app.data.repository.IpCallStatsRepository
import com.smartdialer.app.data.repository.SpamProtectionManager
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
    private var isIpCallActive = false

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

    private var appContext: Context? = null

    fun registerInCallService(service: CallService) {
        inCallService = service
        appContext = service.applicationContext
    }

    fun unregisterInCallService() {
        inCallService = null
    }

    fun setCall(call: Call, context: Context) {
        appContext = context.applicationContext
        activeCall?.unregisterCallback(callCallback)
        activeCall = call
        call.registerCallback(callCallback)
        updateFromCall(call)

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val isIncoming = (call.state == Call.STATE_RINGING)

        // If it's an incoming call while device is unlocked (user in app/game),
        // we DO NOT aggressively pop up CallActivity full-screen.
        // Instead, the floating Heads-Up notification card is displayed at top.
        // Only launch CallActivity directly if outgoing, or if device is locked.
        if (!isIncoming || isLocked) {
            val intent = Intent(context, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    fun clearCall() {
        activeCall?.unregisterCallback(callCallback)
        activeCall = null
        stopDurationTimer()
        appContext?.let {
            CallNotificationManager.dismissAll(it)
            IpCallService.stop(it)
        }
        _currentCallState.value = ActiveCallInfo(status = CallStatus.DISCONNECTED)
    }

    private fun lookupContactInfo(context: Context, number: String): Pair<String?, String?> {
        if (number.isBlank()) return null to null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val thumbIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val photo = if (photoIdx != -1) cursor.getString(photoIdx) else null
                    val thumb = if (thumbIdx != -1) cursor.getString(thumbIdx) else null
                    return name to (photo ?: thumb)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null to null
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

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            call.state == Call.STATE_RINGING
        }

        if (status == CallStatus.ACTIVE && durationJob == null) {
            startDurationTimer()
        } else if (status != CallStatus.ACTIVE && status != CallStatus.HOLDING) {
            stopDurationTimer()
        }

        val context = appContext
        var contactName = callerName
        var contactPhoto: String? = null
        var isSpam = false
        var isSilencedSpam = false
        var isBlocked = false
        var spamCategory: String? = null

        if (context != null && rawNumber.isNotBlank()) {
            val (resolvedName, resolvedPhoto) = lookupContactInfo(context, rawNumber)
            if (contactName.isBlank() && !resolvedName.isNullOrBlank()) {
                contactName = resolvedName
            }
            contactPhoto = resolvedPhoto

            val spamResult = SpamProtectionManager.checkNumber(context, rawNumber, contactName)
            if (spamResult.isSpam) {
                isSpam = true
                isBlocked = spamResult.isBlocked
                spamCategory = spamResult.category

                if (status == CallStatus.RINGING) {
                    if (spamResult.action == SpamAction.REJECT) {
                        reject()
                        return
                    } else if (spamResult.action == SpamAction.SILENCE) {
                        isSilencedSpam = true
                        try {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            audioManager?.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        _currentCallState.value = _currentCallState.value.copy(
            callId = call.toString(),
            number = rawNumber,
            contactName = contactName,
            photoUri = contactPhoto,
            isSpam = isSpam,
            isBlocked = isBlocked,
            isSilencedSpam = isSilencedSpam,
            spamCategory = spamCategory,
            status = status,
            isMuted = isMuted,
            isSpeakerOn = isSpeaker,
            isHolding = (status == CallStatus.HOLDING),
            isIncoming = isIncoming
        )

        // Notification management for Incoming (Heads-Up) and Ongoing states
        if (context != null) {
            when (status) {
                CallStatus.RINGING -> {
                    if (isIncoming) {
                        CallNotificationManager.showIncomingCallNotification(
                            context = context,
                            rawNumber = rawNumber,
                            contactName = contactName,
                            photoUri = contactPhoto,
                            isSpam = isSpam,
                            spamCategory = spamCategory
                        )
                    }
                }
                CallStatus.ACTIVE -> {
                    CallNotificationManager.dismissIncomingCallNotification(context)
                    CallNotificationManager.showOngoingCallNotification(
                        context = context,
                        rawNumber = rawNumber,
                        contactName = contactName,
                        photoUri = contactPhoto,
                        durationFormatted = _currentCallState.value.durationFormatted
                    )
                }
                CallStatus.DISCONNECTED -> {
                    CallNotificationManager.dismissAll(context)
                }
                else -> {
                    // IDLE, CONNECTING, DIALING, HOLDING
                }
            }
        }
    }

    private fun startDurationTimer() {
        durationJob?.cancel()
        durationJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                val updatedState = _currentCallState.value.copy(durationSeconds = seconds)
                _currentCallState.value = updatedState
                if (seconds % 5 == 0L) {
                    appContext?.let { ctx ->
                        CallNotificationManager.showOngoingCallNotification(
                            context = ctx,
                            rawNumber = updatedState.number,
                            contactName = updatedState.contactName,
                            photoUri = updatedState.photoUri,
                            durationFormatted = updatedState.durationFormatted
                        )
                    }
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    fun answer() {
        if (isIpCallActive) {
            SipEngine.answerIncomingCall()
            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.ACTIVE)
            startDurationTimer()
            appContext?.let { ctx ->
                CallNotificationManager.dismissIncomingCallNotification(ctx)
                IpCallService.start(
                    ctx,
                    _currentCallState.value.number,
                    _currentCallState.value.contactName,
                    "Call in progress"
                )
            }
            return
        }
        activeCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        appContext?.let { CallNotificationManager.dismissIncomingCallNotification(it) }
    }

    fun reject() {
        if (isIpCallActive) {
            val num = _currentCallState.value.number
            val name = _currentCallState.value.contactName
            isIpCallActive = false
            SipEngine.rejectIncomingCall()
            stopDurationTimer()
            appContext?.let { ctx ->
                IpCallStatsRepository.recordCall(ctx, num, name, CallType.REJECTED, 0)
                CallNotificationManager.dismissAll(ctx)
                IpCallService.stop(ctx)
            }
            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
            scope.launch {
                delay(1200)
                clearCall()
            }
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activeCall?.reject(Call.REJECT_REASON_DECLINED)
        } else {
            @Suppress("DEPRECATION")
            activeCall?.reject(false, null as? String)
        }
        appContext?.let { CallNotificationManager.dismissAll(it) }
        clearCall()
    }

    fun disconnect() {
        if (isIpCallActive) {
            val num = _currentCallState.value.number
            val name = _currentCallState.value.contactName
            val duration = _currentCallState.value.durationSeconds
            val isIncoming = _currentCallState.value.isIncoming
            val wasActive = _currentCallState.value.status == CallStatus.ACTIVE
            val callType = when {
                wasActive -> if (isIncoming) CallType.INCOMING else CallType.OUTGOING
                isIncoming -> CallType.MISSED
                else -> CallType.OUTGOING
            }
            isIpCallActive = false
            SipEngine.endCall()
            stopDurationTimer()
            appContext?.let { ctx ->
                IpCallStatsRepository.recordCall(ctx, num, name, callType, duration)
                CallNotificationManager.dismissAll(ctx)
                IpCallService.stop(ctx)
            }
            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
            scope.launch {
                delay(1200)
                clearCall()
            }
            return
        }
        activeCall?.disconnect()
        appContext?.let { CallNotificationManager.dismissAll(it) }
        clearCall()
    }

    fun toggleMute() {
        val currentMute = _currentCallState.value.isMuted
        val targetMute = !currentMute
        if (isIpCallActive) {
            SipEngine.setMuted(targetMute)
        } else {
            inCallService?.setMuted(targetMute)
        }
        _currentCallState.value = _currentCallState.value.copy(isMuted = targetMute)
    }

    fun toggleSpeaker(context: Context) {
        val currentSpeaker = _currentCallState.value.isSpeakerOn
        val targetSpeaker = !currentSpeaker
        if (isIpCallActive) {
            SipEngine.setSpeaker(context, targetSpeaker)
        } else {
            val targetRoute = if (currentSpeaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
            inCallService?.setAudioRoute(targetRoute)
        }
        _currentCallState.value = _currentCallState.value.copy(isSpeakerOn = targetSpeaker)
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
        if (isIpCallActive) {
            SipEngine.sendDtmf(digit)
            return
        }
        activeCall?.playDtmfTone(digit)
        scope.launch {
            delay(200)
            activeCall?.stopDtmfTone()
        }
    }

    fun initiateIpCall(context: Context, number: String) {
        if (number.isBlank()) return
        try {
            val cleanNumber = number.trim()
            appContext = context.applicationContext

            val (resolvedName, resolvedPhoto) = lookupContactInfo(context, cleanNumber)
            val displayName = if (!resolvedName.isNullOrBlank()) resolvedName else cleanNumber

            isIpCallActive = true
            _currentCallState.value = ActiveCallInfo(
                callId = "ip_${System.currentTimeMillis()}",
                number = cleanNumber,
                contactName = displayName,
                photoUri = resolvedPhoto,
                status = CallStatus.CONNECTING,
                simSlot = 3,
                isIpCall = true,
                isIncoming = false
            )

            // Launch CallActivity directly without any system resolver or chooser
            val intent = Intent(context, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)

            // Start Foreground Service to keep CPU, network and microphone alive when screen turns off
            IpCallService.start(context, cleanNumber, displayName, "Connecting...")

            // Start pure SIP Engine
            SipEngine.startCall(
                destination = cleanNumber,
                context = context,
                onStateChange = { status, errorReason ->
                when (status) {
                    CallStatus.CONNECTING -> {
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.CONNECTING)
                    }
                    CallStatus.RINGING -> {
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.RINGING)
                        appContext?.let { IpCallService.start(it, cleanNumber, displayName, "Ringing...") }
                    }
                    CallStatus.ACTIVE -> {
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.ACTIVE)
                        startDurationTimer()
                        appContext?.let { IpCallService.start(it, cleanNumber, displayName, "Call in progress") }
                    }
                    CallStatus.DISCONNECTED -> {
                        val duration = _currentCallState.value.durationSeconds
                        stopDurationTimer()
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
                        isIpCallActive = false
                        appContext?.let { ctx ->
                            IpCallStatsRepository.recordCall(ctx, cleanNumber, displayName, CallType.OUTGOING, duration)
                            IpCallService.stop(ctx)
                        }
                        scope.launch {
                            delay(1200)
                            clearCall()
                        }
                    }
                    else -> {}
                }
            }
        )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun handleIncomingIpCall(context: Context, number: String, callerDisplayName: String?) {
        appContext = context.applicationContext
        val (resolvedName, resolvedPhoto) = lookupContactInfo(context, number)
        val displayName = when {
            !resolvedName.isNullOrBlank() -> resolvedName
            !callerDisplayName.isNullOrBlank() -> callerDisplayName
            else -> number
        }

        val spamResult = SpamProtectionManager.checkNumber(context, number, displayName)
        if (spamResult.isSpam && spamResult.action == SpamAction.REJECT) {
            SipEngine.rejectIncomingCall()
            return
        }

        isIpCallActive = true
        _currentCallState.value = ActiveCallInfo(
            callId = "ip_in_${System.currentTimeMillis()}",
            number = number,
            contactName = displayName,
            photoUri = resolvedPhoto,
            isSpam = spamResult.isSpam,
            spamCategory = spamResult.category,
            status = CallStatus.RINGING,
            simSlot = 3,
            isIpCall = true,
            isIncoming = true
        )

        // Show floating Heads-Up notification card with Answer & Decline
        CallNotificationManager.showIncomingCallNotification(
            context = context,
            rawNumber = number,
            contactName = displayName,
            photoUri = resolvedPhoto,
            isSpam = spamResult.isSpam,
            spamCategory = spamResult.category
        )

        // If screen is locked, launch full-screen CallActivity
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            val intent = Intent(context, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }

    fun handleIncomingIpCallCancelled() {
        if (isIpCallActive && _currentCallState.value.status == CallStatus.RINGING) {
            val num = _currentCallState.value.number
            val name = _currentCallState.value.contactName
            isIpCallActive = false
            stopDurationTimer()
            appContext?.let { ctx ->
                IpCallStatsRepository.recordCall(ctx, num, name, CallType.MISSED, 0)
                CallNotificationManager.dismissAll(ctx)
                IpCallService.stop(ctx)
            }
            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
            scope.launch {
                delay(1200)
                clearCall()
            }
        }
    }

    fun initiateCall(context: Context, number: String, simSlot: Int = 0) {
        if (number.isBlank()) return
        try {
            val cleanNumber = number.replace("#", "%23")
            val uri = Uri.parse("tel:$cleanNumber")
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

            val hasPhonePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            // Find real PhoneAccountHandle for the requested simSlot
            var targetAccount: PhoneAccountHandle? = null
            if (telecomManager != null && hasPhoneStatePermission) {
                try {
                    val callCapableAccounts: List<PhoneAccountHandle> = telecomManager.callCapablePhoneAccounts
                    if (callCapableAccounts.isNotEmpty()) {
                        targetAccount = callCapableAccounts.getOrNull(simSlot) ?: callCapableAccounts.firstOrNull()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 1. Direct Telecom placeCall (places call directly via default dialer without chooser popup)
            if (telecomManager != null && hasPhonePermission) {
                try {
                    val extras = android.os.Bundle().apply {
                        if (targetAccount != null) {
                            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetAccount)
                        }
                        putInt("com.android.phone.extra.slot", simSlot)
                        putInt("simSlot", simSlot)
                        putInt("phone_account_id", simSlot)
                        putInt("Subscription", simSlot)
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
                    }
                    telecomManager.placeCall(uri, extras)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Fallback: ACTION_CALL or ACTION_DIAL Intent
            val action = if (hasPhonePermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (targetAccount != null) {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetAccount)
                }
                putExtra("com.android.phone.extra.slot", simSlot)
                putExtra("simSlot", simSlot)
                putExtra("phone_account_id", simSlot)
                putExtra("Subscription", simSlot)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to standard ACTION_DIAL without account extra
                try {
                    val dialFallback = Intent(Intent.ACTION_DIAL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(dialFallback)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
