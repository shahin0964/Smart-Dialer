package com.smartdialer.app

import android.app.Application
import com.smartdialer.app.data.repository.OtaUpdateManager
import com.smartdialer.app.telecom.SipEngine
import com.smartdialer.app.util.AppAnalysisLogger

class SmartDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize App Health & Crash Analysis Logger
        AppAnalysisLogger.init(this)

        // Automatically clean up any downloaded update APKs from storage
        OtaUpdateManager.cleanUpOldApks(this)

        // Start persistent SIP listener for incoming IP calls
        try {
            SipEngine.startListening(this)
        } catch (e: Exception) {
            AppAnalysisLogger.error("Application", "Failed to start SIP listening", e)
        }
    }
}
