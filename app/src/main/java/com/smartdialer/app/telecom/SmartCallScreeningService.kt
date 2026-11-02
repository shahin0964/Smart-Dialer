package com.smartdialer.app.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.smartdialer.app.data.repository.SpamAction
import com.smartdialer.app.data.repository.SpamProtectionManager

/**
 * Telecom CallScreeningService for Android 10+ (API 29+).
 * Automatically intercepts incoming calls at the OS Telecom subsystem level
 * to screen against Spam Protection layers, blocklists, and fraud detection.
 */
class SmartCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        if (rawNumber.isBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val spamResult = SpamProtectionManager.checkNumber(applicationContext, rawNumber, null)
        val shouldDisallow = spamResult.isBlocked || spamResult.action == SpamAction.REJECT
        val shouldSilence = spamResult.action == SpamAction.SILENCE

        val response = CallResponse.Builder().apply {
            if (shouldDisallow) {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipCallLog(false)
                setSkipNotification(true)
            } else if (shouldSilence && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setSilenceCall(true)
                setSkipNotification(false)
            }
        }.build()

        respondToCall(callDetails, response)
    }
}
