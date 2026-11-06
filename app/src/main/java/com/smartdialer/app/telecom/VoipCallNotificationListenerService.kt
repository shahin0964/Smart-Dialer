package com.smartdialer.app.telecom

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartdialer.app.data.repository.AppSettingsPreferences

class VoipCallNotificationListenerService : NotificationListenerService() {

    private lateinit var appSettings: AppSettingsPreferences
    private var activeVoipKey: String? = null

    override fun onCreate() {
        super.onCreate()
        appSettings = AppSettingsPreferences(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!appSettings.voipIntegrationEnabled) return

        val pkg = sbn.packageName ?: return
        val appName = when {
            pkg == "com.facebook.orca" -> "Messenger"
            pkg.startsWith("org.telegram") -> "Telegram"
            pkg.startsWith("com.whatsapp") -> "WhatsApp"
            pkg == "com.instagram.android" -> "Instagram"
            else -> null
        } ?: return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val category = notification.category
        val isCallCategory = (category == Notification.CATEGORY_CALL)

        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
            ?: appName
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val isCallText = text.contains("incoming", ignoreCase = true) ||
                text.contains("call", ignoreCase = true) ||
                text.contains("ইনকামিং", ignoreCase = true) ||
                text.contains("কল", ignoreCase = true)

        val actions = notification.actions ?: emptyArray()
        var answerAction: Notification.Action? = null
        var declineAction: Notification.Action? = null

        for (action in actions) {
            val actTitle = action.title?.toString()?.lowercase() ?: ""
            if (actTitle.contains("answer") || actTitle.contains("accept") || actTitle.contains("receive") || actTitle.contains("উত্তর")) {
                answerAction = action
            } else if (actTitle.contains("decline") || actTitle.contains("reject") || actTitle.contains("dismiss") || actTitle.contains("বাতিল")) {
                declineAction = action
            }
        }

        val isIncomingVoipCall = isCallCategory || (answerAction != null && declineAction != null) || isCallText

        if (isIncomingVoipCall && (answerAction != null || declineAction != null)) {
            activeVoipKey = sbn.key
            CallManager.setIncomingVoipCall(
                context = applicationContext,
                callerName = title,
                appName = appName,
                answerIntent = answerAction?.actionIntent,
                declineIntent = declineAction?.actionIntent
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        if (sbn.key == activeVoipKey) {
            activeVoipKey = null
            CallManager.clearIncomingVoipCall()
        }
    }
}
