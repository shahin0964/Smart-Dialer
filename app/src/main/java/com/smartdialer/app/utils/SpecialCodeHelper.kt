package com.smartdialer.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager

object SpecialCodeHelper {

    /**
     * Checks if the given input is a complete secret code or IMEI query.
     */
    fun isCompleteSpecialCode(input: String): Boolean {
        val clean = input.trim()
        if (clean.equals("*#06#", ignoreCase = true) || clean.equals("*#06", ignoreCase = true)) {
            return true
        }
        if (clean.startsWith("*#*#") && clean.endsWith("#*#*") && clean.length > 8) {
            return true
        }
        if (clean.startsWith("*#") && clean.endsWith("#") && clean.length >= 4) {
            val code = clean.substring(2, clean.length - 1)
            if (code == "0*" || code == "0" || code.all { it.isDigit() }) {
                return true
            }
        }
        return false
    }

    /**
     * Executes the special secret code or IMEI action.
     * Returns true if handled.
     */
    fun handleSpecialCode(
        context: Context,
        input: String,
        onShowImeiDialog: ((List<Pair<String, String>>) -> Unit)? = null
    ): Boolean {
        val clean = input.trim()

        // 1. Handle *#06# (IMEI display)
        if (clean.equals("*#06#", ignoreCase = true) || clean.equals("*#06", ignoreCase = true)) {
            val identifiers = getDeviceIdentifiers(context)
            if (onShowImeiDialog != null) {
                onShowImeiDialog(identifiers)
            }
            // Also invoke system special code for "06" (triggers native overlay if supported by OS)
            sendSecretCode(context, "06")
            return true
        }

        // 2. Handle *#*#<code>#*#* (e.g. *#*#4636#*#* for testing menu)
        if (clean.startsWith("*#*#") && clean.endsWith("#*#*") && clean.length > 8) {
            val code = clean.substring(4, clean.length - 4)
            sendSecretCode(context, code)
            return true
        }

        // 3. Handle *#<code># (e.g. *#0*# or OEM diagnostic)
        if (clean.startsWith("*#") && clean.endsWith("#") && clean.length >= 4) {
            val code = clean.substring(2, clean.length - 1)
            sendSecretCode(context, code)
            return true
        }

        return false
    }

    /**
     * Sends secret code to system TelephonyManager and broadcasts ACTION_SECRET_CODE.
     */
    fun sendSecretCode(context: Context, code: String) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm?.sendDialerSpecialCode(code)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val intent = Intent("android.provider.Telephony.SECRET_CODE").apply {
                data = Uri.parse("android_secret_code://$code")
                flags = Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves device IMEI, MEID, and Serial number if permitted.
     */
    fun getDeviceIdentifiers(context: Context): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return list

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Check slot 0 (SIM 1)
                try {
                    val imei0 = tm.getImei(0)
                    if (!imei0.isNullOrBlank()) list.add("IMEI 1" to imei0)
                } catch (e: Exception) {}

                // Check slot 1 (SIM 2)
                try {
                    val imei1 = tm.getImei(1)
                    if (!imei1.isNullOrBlank()) list.add("IMEI 2" to imei1)
                } catch (e: Exception) {}

                // Primary fallback IMEI
                if (list.isEmpty()) {
                    try {
                        val imei = tm.imei
                        if (!imei.isNullOrBlank()) list.add("IMEI" to imei)
                    } catch (e: Exception) {}
                }

                // MEID
                try {
                    val meid = tm.getMeid(0) ?: tm.meid
                    if (!meid.isNullOrBlank()) list.add("MEID" to meid)
                } catch (e: Exception) {}
            } else {
                @Suppress("DEPRECATION")
                val deviceId = tm.deviceId
                if (!deviceId.isNullOrBlank()) list.add("IMEI / Device ID" to deviceId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Serial Number
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val serial = Build.getSerial()
                    if (!serial.isNullOrBlank() && serial != Build.UNKNOWN) {
                        list.add("Serial Number" to serial)
                    }
                } catch (e: Exception) {}
            } else {
                @Suppress("DEPRECATION")
                val serial = Build.SERIAL
                if (!serial.isNullOrBlank() && serial != Build.UNKNOWN) {
                    list.add("Serial Number" to serial)
                }
            }
        } catch (e: Exception) {}

        return list
    }
}
