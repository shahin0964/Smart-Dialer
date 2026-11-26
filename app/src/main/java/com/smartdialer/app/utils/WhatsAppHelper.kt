package com.smartdialer.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

object WhatsAppHelper {

    /**
     * Checks if WhatsApp or WhatsApp Business is installed on the user's device.
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.whatsapp", 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo("com.whatsapp.w4b", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * Normalizes a phone number to standard international digits without '+' or leading zeros.
     * Handles Bangladesh numbers (+8801..., 8801..., 01...) and international numbers.
     */
    fun normalizeForWhatsApp(raw: String): String {
        val clean = raw.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")

        return when {
            clean.startsWith("880") -> clean
            clean.startsWith("00880") -> clean.removePrefix("00")
            clean.startsWith("01") && clean.length == 11 -> "880" + clean.removePrefix("0")
            else -> clean
        }
    }

    /**
     * Directly launches WhatsApp or WhatsApp Business 1-on-1 chat with the given number
     * and optionally pre-fills a message (e.g., for Quick Reject Reply).
     */
    fun openWhatsAppChat(context: Context, phoneNumber: String, message: String? = null): Boolean {
        val international = normalizeForWhatsApp(phoneNumber)
        if (international.isBlank() || international.length < 5) {
            Toast.makeText(context, "Invalid phone number for WhatsApp", Toast.LENGTH_SHORT).show()
            return false
        }

        val url = if (!message.isNullOrBlank()) {
            "https://api.whatsapp.com/send?phone=$international&text=${Uri.encode(message)}"
        } else {
            "https://api.whatsapp.com/send?phone=$international"
        }
        val directUri = Uri.parse(url)

        // 1. Try standard WhatsApp first
        val waIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(waIntent)
            return true
        } catch (e1: Exception) {
            // 2. Try WhatsApp Business
            val bizIntent = Intent(Intent.ACTION_VIEW, directUri).apply {
                setPackage("com.whatsapp.w4b")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(bizIntent)
                return true
            } catch (e2: Exception) {
                // 3. Fallback to generic web intent (which Android routes to WhatsApp or browser)
                val fallbackUrl = if (!message.isNullOrBlank()) {
                    "https://wa.me/$international?text=${Uri.encode(message)}"
                } else {
                    "https://wa.me/$international"
                }
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(fallbackIntent)
                    true
                } catch (e3: Exception) {
                    Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }
}
