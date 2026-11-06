package com.smartdialer.app.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartdialer.app.MainActivity
import com.smartdialer.app.R
import com.smartdialer.app.data.model.UpdateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppUpdateNotifier {

    const val CHANNEL_ID_APP_UPDATES = "smart_dialer_app_updates"
    const val CHANNEL_NAME_APP_UPDATES = "App Updates"
    const val NOTIFICATION_ID_APP_UPDATE = 4001
    const val EXTRA_OPEN_UPDATE_DIALOG = "EXTRA_OPEN_UPDATE_DIALOG"

    private const val PREFS_NAME = "smart_dialer_update_notifier_prefs"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
    private const val KEY_LAST_NOTIFIED_TIME = "last_notified_time"

    private val scope = CoroutineScope(Dispatchers.IO)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID_APP_UPDATES,
                CHANNEL_NAME_APP_UPDATES,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies users when a new version of Smart Dialer is available"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks in background if a newer release exists on GitHub.
     * If found, delivers an Android system notification to inform the user.
     */
    fun checkAndNotify(context: Context) {
        scope.launch {
            try {
                val updateStatus = OtaUpdateManager.checkForUpdates(context)
                if (updateStatus is UpdateStatus.UpdateAvailable) {
                    val release = updateStatus.latestRelease
                    val currentVersion = updateStatus.currentVersion

                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastNotifiedTag = prefs.getString(KEY_LAST_NOTIFIED_TAG, "") ?: ""
                    val lastNotifiedTime = prefs.getLong(KEY_LAST_NOTIFIED_TIME, 0L)
                    val now = System.currentTimeMillis()

                    val isNewTag = !lastNotifiedTag.equals(release.tagName, ignoreCase = true)
                    val isPast24Hours = (now - lastNotifiedTime) > (24 * 60 * 60 * 1000L)

                    // Notify if it's a newly released version or if 24 hours have passed since last reminder
                    if (isNewTag || isPast24Hours) {
                        showUpdateNotification(context, currentVersion, release.tagName, release.body)

                        prefs.edit()
                            .putString(KEY_LAST_NOTIFIED_TAG, release.tagName)
                            .putLong(KEY_LAST_NOTIFIED_TIME, now)
                            .apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateNotification(
        context: Context,
        currentVersion: String,
        newTag: String,
        changelog: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        ensureChannel(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Intent to open MainActivity with update dialog flag
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(context, 7701, intent, flags)

        val cleanChangelog = changelog.lines().take(4).joinToString("\n")

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚀 Smart Dialer এর নতুন আপডেট ($newTag)")
            .setContentText("নতুন ভার্সনে আপডেট করতে এখানে ট্যাপ করুন।")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("বর্তমান ভার্সন: v$currentVersion ➔ নতুন ভার্সন: $newTag\n\nনতুন ফিচার ও বাগ ফিক্স পেতে অ্যাপটি আপডেট করুন:\n$cleanChangelog")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "আপডেট করুন",
                pendingIntent
            )

        notificationManager.notify(NOTIFICATION_ID_APP_UPDATE, builder.build())
    }
}
