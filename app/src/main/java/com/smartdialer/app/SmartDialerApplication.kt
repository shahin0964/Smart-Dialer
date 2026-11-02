package com.smartdialer.app

import android.app.Application
import com.smartdialer.app.telecom.SipEngine

class SmartDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start persistent SIP listener for incoming IP calls
        try {
            SipEngine.startListening(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
