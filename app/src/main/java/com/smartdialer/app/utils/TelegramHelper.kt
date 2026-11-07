package com.smartdialer.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object TelegramHelper {

    private val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram"
    )

    /**
     * Checks if any official or popular Telegram client is installed on the device.
     */
    fun isTelegramInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return TELEGRAM_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Normalizes phone number to international format without plus or spaces for direct t.me links.
     */
    fun normalizeForTelegram(raw: String): String {
        return WhatsAppHelper.normalizeForWhatsApp(raw)
    }

    /**
     * Opens a direct 1-on-1 chat in Telegram with the specified phone number without saving the contact.
     * Uses the official https://t.me/+<number> URI scheme which routes to Telegram app or web fallback.
     */
    fun openTelegramChat(context: Context, phoneNumber: String): Boolean {
        val cleanNumber = normalizeForTelegram(phoneNumber)
        if (cleanNumber.isBlank() || cleanNumber.length < 5) {
            Toast.makeText(context, "Invalid phone number for Telegram", Toast.LENGTH_SHORT).show()
            return false
        }

        val directUri = Uri.parse("https://t.me/+$cleanNumber")

        // 1. Try launching with explicit Telegram package first
        for (pkg in TELEGRAM_PACKAGES) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, directUri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Try next package
            }
        }

        // 2. Fallback to generic URL intent (Android app chooser or browser)
        return try {
            val genericIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericIntent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Telegram is not installed on this device", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
