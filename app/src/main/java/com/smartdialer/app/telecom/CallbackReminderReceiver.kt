package com.smartdialer.app.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartdialer.app.MainActivity
import com.smartdialer.app.R
import com.smartdialer.app.data.repository.CallNotesRepository

class CallbackReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.smartdialer.app.ACTION_CALLBACK_REMINDER"
        const val ACTION_CALL_NOW = "com.smartdialer.app.ACTION_REMINDER_CALL_NOW"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_NUMBER = "reminder_number"
        const val EXTRA_NAME = "reminder_name"
        const val EXTRA_NOTE = "reminder_note"
        private const val CHANNEL_ID = "smart_dialer_callback_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CALL_NOW -> {
                val id = intent.getIntExtra(EXTRA_REMINDER_ID, 0)
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (id != 0) nm?.cancel(id)
                CallNotesRepository.removeReminder(context, id)
                CallManager.initiateCall(context, number, 0)
            }

            ACTION_TRIGGER_REMINDER -> {
                val id = intent.getIntExtra(EXTRA_REMINDER_ID, 3001)
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                val name = intent.getStringExtra(EXTRA_NAME) ?: number
                val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
                CallNotesRepository.removeReminder(context, id)
                showReminderNotification(context, id, number, name, note)
            }
        }
    }

    private fun showReminderNotification(
        context: Context,
        id: Int,
        number: String,
        name: String,
        note: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Callback Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled reminders to call someone back"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val callIntent = Intent(context, CallbackReminderReceiver::class.java).apply {
            action = ACTION_CALL_NOW
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(EXTRA_NUMBER, number)
        }
        val callPending = PendingIntent.getBroadcast(
            context,
            id + 10000,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            id + 20000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (note.isNotBlank()) {
            "Call back $name ($number)\nNote: $note"
        } else {
            "Reminder to call back $name ($number)"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_incoming_call)
            .setContentTitle("Callback Reminder: $name")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_call_answer, "Call Now", callPending)
            .build()

        try {
            nm.notify(id, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
