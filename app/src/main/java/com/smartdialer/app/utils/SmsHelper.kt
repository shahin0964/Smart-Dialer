package com.smartdialer.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SmsHelper {

    /**
     * Opens default SMS / Messaging app directly into the specific conversation
     * thread with the given recipient phone number.
     * Sanitizes raw number (removes spaces, hyphens, brackets) so messaging apps
     * like Google Messages and Samsung Messages reliably match and open the thread.
     */
    fun openSms(context: Context, rawNumber: String) {
        val clean = rawNumber.filter { it.isDigit() || it == '+' }.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$clean")
                putExtra("address", clean)
                putExtra(Intent.EXTRA_PHONE_NUMBER, clean)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Secondary fallback using ACTION_VIEW with sms: URI
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:$clean")
                    putExtra("address", clean)
                    putExtra(Intent.EXTRA_PHONE_NUMBER, clean)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(viewIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Toast.makeText(context, "Could not open messaging app", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
