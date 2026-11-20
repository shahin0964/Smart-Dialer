package com.smartdialer.app.telecom

import android.telecom.Call
import android.telecom.InCallService
import com.smartdialer.app.util.AppAnalysisLogger

class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        AppAnalysisLogger.telecom("Service", "InCallService bound & created by Telecom framework")
        CallManager.registerInCallService(this)
    }

    override fun onDestroy() {
        AppAnalysisLogger.telecom("Service", "InCallService unbound & destroyed")
        CallManager.unregisterInCallService()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val handle = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        AppAnalysisLogger.telecom("CallAdded", "New call added: state=${call.state}, handle=$handle")
        CallManager.setCall(call, applicationContext)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        AppAnalysisLogger.telecom("CallRemoved", "Call removed from Telecom framework")
        CallManager.clearCall()
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        AppAnalysisLogger.telecom("AudioRoute", "Route=${audioState.route}, isMuted=${audioState.isMuted}")
        CallManager.updateAudioState(audioState)
    }
}
