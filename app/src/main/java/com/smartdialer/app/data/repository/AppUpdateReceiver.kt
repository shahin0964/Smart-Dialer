package com.smartdialer.app.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered when Smart Dialer is updated to a new version (MY_PACKAGE_REPLACED).
 * Automatically deletes any downloaded update APKs from storage and cache so no residue is left.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            OtaUpdateManager.cleanUpOldApks(context)
        }
    }
}
