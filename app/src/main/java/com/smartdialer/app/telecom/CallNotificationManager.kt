package com.smartdialer.app.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.smartdialer.app.R
import com.smartdialer.app.ui.call.CallActivity
import com.smartdialer.app.ui.components.ContactPhotoLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages Incoming Call Heads-Up Notifications (HUN) and Ongoing In-Call Notifications.
 * Displays a non-intrusive floating top card with Contact Name, Number, Avatar,
 * and Receive / Decline buttons when user is playing games or using other apps.
 */
object CallNotificationManager {

    private const val CHANNEL_ID_INCOMING = "smart_dialer_incoming_calls_v2"
    private const val CHANNEL_NAME_INCOMING = "Incoming Calls"

    private const val CHANNEL_ID_ONGOING = "smart_dialer_ongoing_calls"
    private const val CHANNEL_NAME_ONGOING = "Ongoing Calls"

    const val NOTIFICATION_ID_INCOMING = 2001
    const val NOTIFICATION_ID_ONGOING = 2002

    private val scope = CoroutineScope(Dispatchers.Main)

    private fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. High-Priority Channel for Incoming Calls (Shows Heads-Up notification card at top)
            val incomingChannel = NotificationChannel(
                CHANNEL_ID_INCOMING,
                CHANNEL_NAME_INCOMING,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows incoming call banner card with answer and decline options"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
                setSound(ringtoneUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(incomingChannel)

            // 2. Ongoing Call Channel (Silent background notification)
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID_ONGOING,
                CHANNEL_NAME_ONGOING,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active call duration and end call shortcut"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        rawNumber: String,
        contactName: String?,
        photoUri: String?,
        isSpam: Boolean,
        spamCategory: String?
    ) {
        ensureChannelsCreated(context)

        val displayName = if (!contactName.isNullOrBlank() && contactName != "Unknown") {
            contactName
        } else {
            rawNumber.ifBlank { "Unknown Caller" }
        }

        val contentText = when {
            isSpam -> "⚠️ Suspected Spam (${spamCategory ?: "Scam"}) • $rawNumber"
            !contactName.isNullOrBlank() && contactName != "Unknown" && rawNumber.isNotBlank() -> rawNumber
            else -> "Incoming call..."
        }

        // Asynchronously check photo and build notification
        scope.launch {
            var avatarBitmap: Bitmap? = null
            if (!photoUri.isNullOrBlank()) {
                avatarBitmap = ContactPhotoLoader.loadPhoto(context, photoUri, null, preferHighRes = true)
            }
            if (avatarBitmap == null) {
                avatarBitmap = createMonogramBitmap(displayName)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Intent when user taps Decline (Red button)
            val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE_CALL
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Intent when user taps Answer (Green button)
            val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ANSWER_CALL
            }
            val answerPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Intent when user taps the notification card body -> opens full CallActivity
            val fullScreenIntent = Intent(context, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                103,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val callerIcon = IconCompat.createWithBitmap(avatarBitmap)
            val person = Person.Builder()
                .setName(displayName)
                .setIcon(callerIcon)
                .setImportant(true)
                .build()

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_INCOMING)
                .setSmallIcon(R.drawable.ic_notification_incoming_call)
                .setContentTitle(displayName)
                .setContentText(contentText)
                .setSubText(if (isSpam) "⚠️ Spam Warning" else "Incoming Call")
                .setLargeIcon(avatarBitmap)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true) // Shows heads-up card when unlocked!
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        declinePendingIntent,
                        answerPendingIntent
                    )
                )
                .addAction(R.drawable.ic_call_decline, "Decline", declinePendingIntent)
                .addAction(R.drawable.ic_call_answer, "Answer", answerPendingIntent)

            try {
                notificationManager.notify(NOTIFICATION_ID_INCOMING, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showOngoingCallNotification(
        context: Context,
        rawNumber: String,
        contactName: String?,
        photoUri: String?,
        durationFormatted: String
    ) {
        ensureChannelsCreated(context)

        val displayName = if (!contactName.isNullOrBlank() && contactName != "Unknown") {
            contactName
        } else {
            rawNumber.ifBlank { "Caller" }
        }

        scope.launch {
            var avatarBitmap: Bitmap? = null
            if (!photoUri.isNullOrBlank()) {
                avatarBitmap = ContactPhotoLoader.loadPhoto(context, photoUri, null, false)
            }
            if (avatarBitmap == null) {
                avatarBitmap = createMonogramBitmap(displayName)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val hangupIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DISCONNECT_CALL
            }
            val hangupPendingIntent = PendingIntent.getBroadcast(
                context,
                201,
                hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val openCallIntent = Intent(context, CallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openCallPendingIntent = PendingIntent.getActivity(
                context,
                202,
                openCallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_ONGOING)
                .setSmallIcon(R.drawable.ic_notification_incoming_call)
                .setContentTitle(displayName)
                .setContentText("Call in progress • $durationFormatted")
                .setLargeIcon(avatarBitmap)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(openCallPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(R.drawable.ic_call_decline, "End Call", hangupPendingIntent)

            try {
                notificationManager.notify(NOTIFICATION_ID_ONGOING, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createOngoingNotification(
        context: Context,
        rawNumber: String,
        contactName: String?,
        statusText: String = "Call in progress"
    ): android.app.Notification {
        ensureChannelsCreated(context)

        val displayName = if (!contactName.isNullOrBlank() && contactName != "Unknown") {
            contactName
        } else {
            rawNumber.ifBlank { "Smart Dialer" }
        }

        val avatarBitmap = createMonogramBitmap(displayName)

        val hangupIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DISCONNECT_CALL
        }
        val hangupPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openCallIntent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openCallPendingIntent = PendingIntent.getActivity(
            context,
            202,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ONGOING)
            .setSmallIcon(R.drawable.ic_notification_incoming_call)
            .setContentTitle(displayName)
            .setContentText(statusText)
            .setLargeIcon(avatarBitmap)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openCallPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_call_decline, "End Call", hangupPendingIntent)
            .build()
    }

    fun dismissIncomingCallNotification(context: Context) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissAll(context: Context) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID_INCOMING)
            notificationManager.cancel(NOTIFICATION_ID_ONGOING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createMonogramBitmap(name: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colors = intArrayOf(
            0xFFDBEAFE.toInt(), // Blue
            0xFFFEF3C7.toInt(), // Amber
            0xFFDCFCE7.toInt(), // Green
            0xFFEDE9FE.toInt(), // Purple
            0xFFFFE4E6.toInt()  // Pink
        )
        val textColors = intArrayOf(
            0xFF1D4ED8.toInt(),
            0xFFB45309.toInt(),
            0xFF15803D.toInt(),
            0xFF6D28D9.toInt(),
            0xFFBE123C.toInt()
        )
        val index = Math.abs(name.hashCode()) % colors.size

        paint.color = colors[index]
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = textColors[index]
        paint.textSize = 52f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val initial = name.trim().firstOrNull()?.uppercase() ?: "#"
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, size / 2f, yPos, paint)

        return bitmap
    }
}
