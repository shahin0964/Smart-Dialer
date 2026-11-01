package com.smartdialer.app.telecom

import android.telecom.Call
import android.telecom.InCallService

class CallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.registerInCallService(this)
    }

    override fun onDestroy() {
        CallManager.unregisterInCallService()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setCall(call, applicationContext)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.clearCall()
    }
}
