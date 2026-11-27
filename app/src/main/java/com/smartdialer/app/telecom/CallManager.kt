package com.smartdialer.app.telecom

import android.Manifest
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
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
import android.telephony.TelephonyManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.ActiveCallInfo
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.model.EndedCallSummary
import com.smartdialer.app.data.model.SpamAction
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.data.repository.CallNotesRepository
import com.smartdialer.app.data.repository.IpCallStatsRepository
import com.smartdialer.app.data.repository.SpamProtectionManager
import com.smartdialer.app.ui.call.CallActivity
import com.smartdialer.app.utils.SpecialCodeHelper
import com.smartdialer.app.utils.VibrationHelper
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

    private val _lastEndedCall = MutableStateFlow<EndedCallSummary?>(null)
    val lastEndedCall: StateFlow<EndedCallSummary?> = _lastEndedCall.asStateFlow()

    private val trackedCalls = mutableListOf<Call>()
    private var activeCall: Call? = null
    private var inCallService: CallService? = null
    private var isIpCallActive = false
    private var wasCurrentCallAnswered = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (call == activeCall) {
                updateFromCall(call)
            } else {
                refreshSecondaryCallInfo()
            }
        }
    }

    fun dismissPostCallSummary() {
        _lastEndedCall.value = null
    }

    private fun emitPostCallSummary(info: ActiveCallInfo, wasAnswered: Boolean) {
        val ctx = appContext ?: return
        if (info.number.isBlank() || info.isBlocked) return
        val prefs = AppSettingsPreferences(ctx)
        if (!prefs.postCallSummaryEnabled) return
        _lastEndedCall.value = EndedCallSummary(
            number = info.number,
            contactName = info.contactName,
            photoUri = info.photoUri,
            durationSeconds = info.durationSeconds,
            isIncoming = info.isIncoming,
            isIpCall = info.isIpCall,
            wasAnswered = wasAnswered
        )
    }

    fun updateAudioState(audioState: CallAudioState) {
        _currentCallState.value = _currentCallState.value.copy(
            isMuted = audioState.isMuted,
            isSpeakerOn = (audioState.route == CallAudioState.ROUTE_SPEAKER),
            isBluetoothOn = (audioState.route == CallAudioState.ROUTE_BLUETOOTH)
        )
    }

    fun updateIpAudioRoute(isSpeaker: Boolean, isBluetooth: Boolean) {
        _currentCallState.value = _currentCallState.value.copy(
            isSpeakerOn = isSpeaker,
            isBluetoothOn = isBluetooth
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

    fun launchCallActivity(context: Context) {
        val intent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }.toBundle()
        } else {
            null
        }

        // 1. Direct startActivity with BAL options
        try {
            if (options != null) {
                context.startActivity(intent, options)
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Also send via PendingIntent with MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (options != null) {
                pendingIntent.send(context, 0, intent, null, null, null, options)
            } else {
                pendingIntent.send()
            }
        } catch (ex: Exception) {
            // Ignored if already launched
        }
    }

    fun setCall(call: Call, context: Context) {
        appContext = context.applicationContext
        if (!trackedCalls.contains(call)) {
            trackedCalls.add(call)
            call.registerCallback(callCallback)
        }

        val hadActiveCall = activeCall != null && activeCall != call &&
            activeCall?.state != Call.STATE_DISCONNECTED

        if (hadActiveCall && call.state == Call.STATE_RINGING) {
            // Call Waiting: keep existing activeCall primary, expose waiting call in secondary banner
            refreshSecondaryCallInfo()
            val waitingNumber = call.details?.handle?.schemeSpecificPart ?: ""
            val (wName, wPhoto) = lookupContactInfo(context, waitingNumber)
            CallNotificationManager.showIncomingCallNotification(
                context = context,
                rawNumber = waitingNumber,
                contactName = wName,
                photoUri = wPhoto,
                isSpam = false,
                spamCategory = null
            )
            return
        }

        // Promote new outgoing or newly foregrounded call to primary
        activeCall = call
        wasCurrentCallAnswered = (call.state == Call.STATE_ACTIVE)
        updateFromCall(call)

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val isIncoming = (call.state == Call.STATE_RINGING)

        if (!isIncoming || isLocked) {
            launchCallActivity(context)
        }
    }

    fun removeCall(call: Call) {
        call.unregisterCallback(callCallback)
        trackedCalls.remove(call)

        val removedInfo = if (call == activeCall) _currentCallState.value else null
        val removedWasAnswered = wasCurrentCallAnswered

        // Switch to remaining tracked call if any (Multi-call continuity)
        val remaining = trackedCalls.lastOrNull { it.state != Call.STATE_DISCONNECTED }
        if (remaining != null) {
            activeCall = remaining
            updateFromCall(remaining)
            return
        }

        if (removedInfo != null && removedInfo.number.isNotBlank()) {
            emitPostCallSummary(removedInfo, removedWasAnswered)
        }
        clearCall()
    }

    fun clearCall() {
        trackedCalls.forEach {
            try {
                it.unregisterCallback(callCallback)
            } catch (_: Exception) {
            }
        }
        trackedCalls.clear()
        activeCall = null
        wasCurrentCallAnswered = false
        stopDurationTimer()
        stopRecordingInternal()
        SmartCallControlsManager.stopAll()
        appContext?.let {
            CallNotificationManager.dismissAll(it)
            IpCallService.stop(it)
        }
        _currentCallState.value = ActiveCallInfo(status = CallStatus.DISCONNECTED)
    }

    private fun refreshSecondaryCallInfo() {
        val secondary = trackedCalls.firstOrNull {
            it != activeCall && it.state != Call.STATE_DISCONNECTED
        }
        if (secondary == null) {
            _currentCallState.value = _currentCallState.value.copy(
                hasSecondaryCall = false,
                secondaryCallName = "",
                secondaryCallNumber = "",
                secondaryCallStatus = CallStatus.IDLE,
                canMergeCalls = false
            )
            return
        }
        val secNumber = secondary.details?.handle?.schemeSpecificPart ?: ""
        val secName = appContext?.let { lookupContactInfo(it, secNumber).first }
            ?: secondary.details?.callerDisplayName
            ?: secNumber
        val secStatus = when (secondary.state) {
            Call.STATE_RINGING -> CallStatus.RINGING
            Call.STATE_ACTIVE -> CallStatus.ACTIVE
            Call.STATE_HOLDING -> CallStatus.HOLDING
            Call.STATE_DIALING -> CallStatus.DIALING
            Call.STATE_CONNECTING -> CallStatus.CONNECTING
            else -> CallStatus.IDLE
        }
        val canMerge = activeCall?.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true ||
            (trackedCalls.size > 1 && secondary.state != Call.STATE_RINGING)
        _currentCallState.value = _currentCallState.value.copy(
            hasSecondaryCall = true,
            secondaryCallName = secName.ifBlank { secNumber },
            secondaryCallNumber = secNumber,
            secondaryCallStatus = secStatus,
            canMergeCalls = canMerge
        )
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

    private fun resolveSubIdForSlot(context: Context?, simSlot: Int): Int {
        if (context == null || simSlot < 0) return -1
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return -1
        }
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            sm?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == simSlot }?.subscriptionId ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun resolveCallSimInfo(context: Context?, call: Call, fallbackSlot: Int): Pair<Int, Int> {
        if (context == null) return fallbackSlot to -1
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return fallbackSlot to -1
        }
        return try {
            val handle = call.details?.accountHandle
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subs = sm?.activeSubscriptionInfoList

            if (handle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val subId = tm?.getSubscriptionId(handle) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subId > 0) {
                    val slot = subs?.firstOrNull { it.subscriptionId == subId }?.simSlotIndex ?: fallbackSlot
                    return slot to subId
                }
            }

            if (handle != null) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val idx = telecomManager?.callCapablePhoneAccounts?.indexOf(handle) ?: -1
                if (idx >= 0) {
                    val subId = subs?.firstOrNull { it.simSlotIndex == idx }?.subscriptionId ?: -1
                    return idx to subId
                }
            }

            val subId = subs?.firstOrNull { it.simSlotIndex == fallbackSlot }?.subscriptionId ?: -1
            fallbackSlot to subId
        } catch (_: Exception) {
            fallbackSlot to -1
        }
    }

    private fun updateFromCall(call: Call) {
        val previousStatus = _currentCallState.value.status
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
        val isBluetooth = (audioState?.route == CallAudioState.ROUTE_BLUETOOTH)

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            call.state == Call.STATE_RINGING
        }

        if (status == CallStatus.ACTIVE) {
            wasCurrentCallAnswered = true
            SmartCallControlsManager.stopAll()
            if (durationJob == null) {
                startDurationTimer()
            }
        } else if (status != CallStatus.HOLDING) {
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

        val activeNote = context?.let {
            if (rawNumber.isNotBlank()) CallNotesRepository.getNoteForNumber(it, rawNumber)?.note else null
        }

        val isWifiCalling = call.details?.hasProperty(Call.Details.PROPERTY_WIFI) == true
        val (resolvedSlot, resolvedSubId) = resolveCallSimInfo(context, call, _currentCallState.value.simSlot)

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
            isBluetoothOn = isBluetooth,
            isHolding = (status == CallStatus.HOLDING),
            simSlot = resolvedSlot,
            subscriptionId = resolvedSubId,
            isIpCall = false,
            isWifiCalling = isWifiCalling,
            isIncoming = isIncoming,
            isRecording = CallRecorderManager.isRecording(),
            isConference = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true,
            activeNote = activeNote
        )
        refreshSecondaryCallInfo()

        if (context != null && previousStatus != CallStatus.RINGING && status == CallStatus.RINGING && isIncoming) {
            SmartCallControlsManager.onIncomingRingingStarted(
                context = context,
                displayName = contactName,
                rawNumber = rawNumber,
                isSilencedSpam = isSilencedSpam
            )
        }

        // Tactile vibration feedback & auto-recording on call connect for SIM calls
        if (context != null) {
            if (previousStatus != CallStatus.ACTIVE && status == CallStatus.ACTIVE) {
                VibrationHelper.vibrateCallConnected(context)
                tryAutoStartRecording(context, rawNumber, contactName, isIpCall = false)
            } else if (previousStatus != CallStatus.DISCONNECTED && status == CallStatus.DISCONNECTED) {
                VibrationHelper.vibrateCallEnded(context)
            }
        }

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
                    } else {
                        CallNotificationManager.showOngoingCallNotification(
                            context = context,
                            rawNumber = rawNumber,
                            contactName = contactName,
                            photoUri = contactPhoto,
                            statusText = "Ringing..."
                        )
                    }
                }
                CallStatus.DIALING, CallStatus.CONNECTING -> {
                    if (!isIncoming) {
                        CallNotificationManager.showOngoingCallNotification(
                            context = context,
                            rawNumber = rawNumber,
                            contactName = contactName,
                            photoUri = contactPhoto,
                            statusText = if (status == CallStatus.DIALING) "Dialing..." else "Connecting..."
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
                CallStatus.HOLDING -> {
                    CallNotificationManager.showOngoingCallNotification(
                        context = context,
                        rawNumber = rawNumber,
                        contactName = contactName,
                        photoUri = contactPhoto,
                        statusText = "Call on hold • ${_currentCallState.value.durationFormatted}"
                    )
                }
                CallStatus.DISCONNECTED -> {
                    CallNotificationManager.dismissAll(context)
                }
                else -> {
                    // IDLE
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
                val quality = if (isIpCallActive) SipEngine.networkQuality.value else null
                val updatedState = _currentCallState.value.copy(
                    durationSeconds = seconds,
                    networkQuality = quality
                )
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
        SmartCallControlsManager.stopAll()
        if (isIpCallActive) {
            wasCurrentCallAnswered = true
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
                VibrationHelper.vibrateCallConnected(ctx)
                tryAutoStartRecording(ctx, _currentCallState.value.number, _currentCallState.value.contactName, isIpCall = true)
            }
            return
        }
        // If answering a waiting cellular call while another call is active, hold current and promote waiting
        val waitingCall = trackedCalls.firstOrNull { it != activeCall && it.state == Call.STATE_RINGING }
        if (waitingCall != null) {
            activeCall?.hold()
            activeCall = waitingCall
            waitingCall.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            updateFromCall(waitingCall)
            appContext?.let { CallNotificationManager.dismissIncomingCallNotification(it) }
            return
        }
        activeCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.ACTIVE)
        startDurationTimer()
        appContext?.let { ctx ->
            CallNotificationManager.dismissIncomingCallNotification(ctx)
            VibrationHelper.vibrateCallConnected(ctx)
        }
    }

    fun reject() {
        SmartCallControlsManager.stopAll()
        if (isIpCallActive) {
            val snapshot = _currentCallState.value
            val num = snapshot.number
            val name = snapshot.contactName
            isIpCallActive = false
            SipEngine.rejectIncomingCall()
            stopDurationTimer()
            stopRecordingInternal()
            appContext?.let { ctx ->
                IpCallStatsRepository.recordCall(ctx, num, name, CallType.REJECTED, 0)
                CallNotificationManager.dismissAll(ctx)
                IpCallService.stop(ctx)
                VibrationHelper.vibrateCallEnded(ctx)
            }
            emitPostCallSummary(snapshot, wasAnswered = false)
            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
            scope.launch {
                delay(1200)
                clearCall()
            }
            return
        }
        val waitingCall = trackedCalls.firstOrNull { it != activeCall && it.state == Call.STATE_RINGING }
        if (waitingCall != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                waitingCall.reject(Call.REJECT_REASON_DECLINED)
            } else {
                @Suppress("DEPRECATION")
                waitingCall.reject(false, null as? String)
            }
            appContext?.let { CallNotificationManager.dismissIncomingCallNotification(it) }
            refreshSecondaryCallInfo()
            return
        }
        val snapshot = _currentCallState.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activeCall?.reject(Call.REJECT_REASON_DECLINED)
        } else {
            @Suppress("DEPRECATION")
            activeCall?.reject(false, null as? String)
        }
        emitPostCallSummary(snapshot, wasAnswered = false)
        appContext?.let { CallNotificationManager.dismissAll(it) }
        clearCall()
    }

    fun disconnect() {
        SmartCallControlsManager.stopAll()
        if (isIpCallActive) {
            val snapshot = _currentCallState.value
            val num = snapshot.number
            val name = snapshot.contactName
            val duration = snapshot.durationSeconds
            val isIncoming = snapshot.isIncoming
            val wasActive = snapshot.status == CallStatus.ACTIVE
            val callType = when {
                wasActive -> if (isIncoming) CallType.INCOMING else CallType.OUTGOING
                isIncoming -> CallType.MISSED
                else -> CallType.OUTGOING
            }
            isIpCallActive = false
            SipEngine.endCall()
            stopDurationTimer()
            stopRecordingInternal()
            appContext?.let { ctx ->
                IpCallStatsRepository.recordCall(ctx, num, name, callType, duration)
                CallNotificationManager.dismissAll(ctx)
                IpCallService.stop(ctx)
                VibrationHelper.vibrateCallEnded(ctx)
            }
            emitPostCallSummary(snapshot, wasAnswered = wasActive)
            _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
            scope.launch {
                delay(1200)
                clearCall()
            }
            return
        }
        val callToDisconnect = activeCall
        val snapshot = _currentCallState.value
        val wasAnswered = wasCurrentCallAnswered
        callToDisconnect?.disconnect()
        if (trackedCalls.size <= 1) {
            emitPostCallSummary(snapshot, wasAnswered = wasAnswered)
            appContext?.let { CallNotificationManager.dismissAll(it) }
            clearCall()
        }
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
            val hasBt = SipEngine.isBluetoothConnected()
            SipEngine.setSpeaker(context, targetSpeaker)
            _currentCallState.value = _currentCallState.value.copy(
                isSpeakerOn = targetSpeaker,
                isBluetoothOn = if (targetSpeaker) false else hasBt
            )
        } else {
            val supported = inCallService?.callAudioState?.supportedRouteMask ?: 0
            val hasBt = (supported and CallAudioState.ROUTE_BLUETOOTH) != 0
            val hasWired = (supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0
            val targetRoute = if (currentSpeaker) {
                if (hasBt) {
                    CallAudioState.ROUTE_BLUETOOTH
                } else if (hasWired) {
                    CallAudioState.ROUTE_WIRED_HEADSET
                } else {
                    CallAudioState.ROUTE_EARPIECE
                }
            } else {
                CallAudioState.ROUTE_SPEAKER
            }
            inCallService?.setAudioRoute(targetRoute)
            _currentCallState.value = _currentCallState.value.copy(
                isSpeakerOn = targetSpeaker,
                isBluetoothOn = if (targetSpeaker) false else hasBt
            )
        }
    }

    fun toggleHold() {
        val call = activeCall ?: return
        if (call.state == Call.STATE_HOLDING) {
            call.unhold()
        } else {
            call.hold()
        }
    }

    fun swapCalls() {
        val current = activeCall ?: return
        val secondary = trackedCalls.firstOrNull {
            it != current && it.state != Call.STATE_DISCONNECTED
        } ?: return

        if (secondary.state == Call.STATE_RINGING) {
            current.hold()
            activeCall = secondary
            secondary.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        } else {
            if (current.state == Call.STATE_ACTIVE) {
                current.hold()
            }
            if (secondary.state == Call.STATE_HOLDING) {
                secondary.unhold()
            }
            activeCall = secondary
        }
        updateFromCall(secondary)
    }

    fun mergeCalls() {
        val current = activeCall ?: return
        val secondary = trackedCalls.firstOrNull {
            it != current && it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_RINGING
        } ?: return
        try {
            current.conference(secondary)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryAutoStartRecording(
        context: Context,
        number: String,
        contactName: String?,
        isIpCall: Boolean
    ) {
        if (CallRecorderManager.isRecording()) return
        val prefs = AppSettingsPreferences(context)
        if (!prefs.autoRecord) return
        val isUnknown = contactName.isNullOrBlank() || contactName == number
        if (prefs.recordUnknownOnly && !isUnknown) return

        val started = CallRecorderManager.startRecording(
            context = context,
            phoneNumber = number,
            contactName = contactName,
            isIpCall = isIpCall,
            audioFormatPref = prefs.audioFormat
        )
        if (started) {
            _currentCallState.value = _currentCallState.value.copy(isRecording = true)
        }
    }

    private fun stopRecordingInternal() {
        if (CallRecorderManager.isRecording()) {
            CallRecorderManager.stopRecording()
        }
        _currentCallState.value = _currentCallState.value.copy(isRecording = false)
    }

    fun toggleRecording() {
        val ctx = appContext ?: return
        if (CallRecorderManager.isRecording()) {
            val savedFile = CallRecorderManager.stopRecording()
            _currentCallState.value = _currentCallState.value.copy(isRecording = false)
            if (savedFile != null) {
                android.widget.Toast.makeText(ctx, "Recording saved: ${savedFile.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            val info = _currentCallState.value
            val prefs = AppSettingsPreferences(ctx)
            val started = CallRecorderManager.startRecording(
                context = ctx,
                phoneNumber = info.number,
                contactName = info.contactName,
                isIpCall = info.isIpCall,
                audioFormatPref = prefs.audioFormat
            )
            _currentCallState.value = _currentCallState.value.copy(isRecording = started)
        }
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
            val activeNote = CallNotesRepository.getNoteForNumber(context, cleanNumber)?.note

            wasCurrentCallAnswered = false
            isIpCallActive = true
            _currentCallState.value = ActiveCallInfo(
                callId = "ip_${System.currentTimeMillis()}",
                number = cleanNumber,
                contactName = displayName,
                photoUri = resolvedPhoto,
                status = CallStatus.CONNECTING,
                simSlot = 3,
                isIpCall = true,
                isIncoming = false,
                activeNote = activeNote
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
                        wasCurrentCallAnswered = true
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.ACTIVE)
                        startDurationTimer()
                        appContext?.let {
                            IpCallService.start(it, cleanNumber, displayName, "Call in progress")
                            VibrationHelper.vibrateCallConnected(it)
                            tryAutoStartRecording(it, cleanNumber, displayName, isIpCall = true)
                        }
                    }
                    CallStatus.DISCONNECTED -> {
                        val snapshot = _currentCallState.value
                        val duration = snapshot.durationSeconds
                        val answered = wasCurrentCallAnswered
                        stopDurationTimer()
                        stopRecordingInternal()
                        _currentCallState.value = _currentCallState.value.copy(status = CallStatus.DISCONNECTED)
                        isIpCallActive = false
                        appContext?.let { ctx ->
                            IpCallStatsRepository.recordCall(ctx, cleanNumber, displayName, CallType.OUTGOING, duration)
                            IpCallService.stop(ctx)
                            VibrationHelper.vibrateCallEnded(ctx)
                            if (!errorReason.isNullOrBlank()) {
                                android.widget.Toast.makeText(ctx, errorReason, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        if (answered || duration > 0L) {
                            emitPostCallSummary(snapshot, wasAnswered = answered)
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

        val activeNote = CallNotesRepository.getNoteForNumber(context, number)?.note
        wasCurrentCallAnswered = false
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
            isIncoming = true,
            activeNote = activeNote
        )

        SmartCallControlsManager.onIncomingRingingStarted(
            context = context,
            displayName = displayName,
            rawNumber = number,
            isSilencedSpam = (spamResult.action == SpamAction.SILENCE)
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
        SmartCallControlsManager.stopAll()
        if (isIpCallActive && _currentCallState.value.status == CallStatus.RINGING) {
            val snapshot = _currentCallState.value
            val num = snapshot.number
            val name = snapshot.contactName
            isIpCallActive = false
            stopDurationTimer()
            appContext?.let { ctx ->
                IpCallStatsRepository.recordCall(ctx, num, name, CallType.MISSED, 0)
                CallNotificationManager.dismissAll(ctx)
                IpCallService.stop(ctx)
                VibrationHelper.vibrateCallEnded(ctx)
            }
            emitPostCallSummary(snapshot, wasAnswered = false)
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
            val cleanNumber = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }.trim()
            if (cleanNumber.isBlank()) return

            // Handle secret codes (e.g. *#06#, *#*#4636#*#*)
            if (SpecialCodeHelper.handleSpecialCode(context, cleanNumber)) {
                return
            }

            val encodedNumber = cleanNumber.replace("#", "%23")
            val uri = Uri.parse("tel:$encodedNumber")
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

            val isUssd = cleanNumber.startsWith("*") && cleanNumber.endsWith("#")

            // For USSD codes (*...#): ACTION_CALL with encoded URI starts the carrier MMI overlay
            if (isUssd && hasPhonePermission) {
                try {
                    val ussdIntent = Intent(Intent.ACTION_CALL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (targetAccount != null) {
                            putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetAccount)
                        }
                        putExtra("com.android.phone.extra.slot", simSlot)
                        putExtra("simSlot", simSlot)
                        putExtra("phone_account_id", simSlot)
                        putExtra("Subscription", simSlot)
                    }
                    context.startActivity(ussdIntent)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            appContext = context.applicationContext
            val (resolvedName, resolvedPhoto) = lookupContactInfo(context, cleanNumber)
            val displayName = if (!resolvedName.isNullOrBlank()) resolvedName else cleanNumber
            val initialSubId = resolveSubIdForSlot(context, simSlot)

            _currentCallState.value = ActiveCallInfo(
                callId = "out_${System.currentTimeMillis()}",
                number = cleanNumber,
                contactName = displayName,
                photoUri = resolvedPhoto,
                status = CallStatus.CONNECTING,
                simSlot = simSlot,
                subscriptionId = initialSubId,
                isIpCall = false,
                isIncoming = false
            )

            // Immediately launch CallActivity from the active foreground context
            launchCallActivity(context)

            // Show outgoing notification immediately so user has persistent call controls
            CallNotificationManager.showOngoingCallNotification(
                context = context,
                rawNumber = cleanNumber,
                contactName = displayName,
                photoUri = resolvedPhoto,
                statusText = "Calling..."
            )

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
