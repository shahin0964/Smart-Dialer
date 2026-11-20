package com.smartdialer.app

import android.app.Application
import com.smartdialer.app.telecom.SipEngine
import com.smartdialer.app.util.AppAnalysisLogger

class SmartDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize App Health & Crash Analysis Logger
        AppAnalysisLogger.init(this)

        // Start persistent SIP listener for incoming IP calls
        try {
            SipEngine.startListening(this)
        } catch (e: Exception) {
            AppAnalysisLogger.error("Application", "Failed to start SIP listening", e)
        }
    }
}
