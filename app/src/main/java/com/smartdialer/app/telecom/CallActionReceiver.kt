package com.smartdialer.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartdialer.app.ui.call.CallActivity

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER_CALL = "com.smartdialer.app.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.smartdialer.app.ACTION_DECLINE_CALL"
        const val ACTION_DISCONNECT_CALL = "com.smartdialer.app.ACTION_DISCONNECT_CALL"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER_CALL -> {
                CallNotificationManager.dismissIncomingCallNotification(context)
                CallManager.answer()

                // Launch CallActivity into foreground
                val callIntent = Intent(context, CallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(callIntent)
            }

            ACTION_DECLINE_CALL -> {
                CallNotificationManager.dismissIncomingCallNotification(context)
                CallManager.reject()
            }

            ACTION_DISCONNECT_CALL -> {
                CallNotificationManager.dismissAll(context)
                CallManager.disconnect()
            }
        }
    }
}
