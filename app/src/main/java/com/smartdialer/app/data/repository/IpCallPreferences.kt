package com.smartdialer.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.smartdialer.app.telecom.CallManager

enum class IpCallMode {
    PREFIX,         // Prepend IP prefix/gateway to destination number (e.g. 096 + number)
    SIP,            // Standard SIP URI (sip:number)
    APP_CHOOSER     // Open installed IP / VoIP calling apps (Alaap, Brilliant, WhatsApp, etc.)
}

class IpCallPreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_dialer_ip_prefs", Context.MODE_PRIVATE)

    var ipNumber: String
        get() = prefs.getString("ip_number", "") ?: ""
        set(value) = prefs.edit().putString("ip_number", value.trim()).apply()

    var ipPrefix: String
        get() = prefs.getString("ip_prefix", "") ?: ""
        set(value) = prefs.edit().putString("ip_prefix", value.trim()).apply()

    var ipGateway: String
        get() = prefs.getString("ip_gateway", "") ?: ""
        set(value) = prefs.edit().putString("ip_gateway", value.trim()).apply()

    var ipMode: IpCallMode
        get() {
            val name = prefs.getString("ip_mode", IpCallMode.PREFIX.name) ?: IpCallMode.PREFIX.name
            return try {
                IpCallMode.valueOf(name)
            } catch (e: Exception) {
                IpCallMode.PREFIX
            }
        }
        set(value) = prefs.edit().putString("ip_mode", value.name).apply()

    val isConfigured: Boolean
        get() = ipNumber.isNotBlank() || ipPrefix.isNotBlank() || ipGateway.isNotBlank()

    fun makeIpCall(destinationNumber: String, simSlot: Int = 0) {
        val clean = destinationNumber.trim()
        if (clean.isEmpty()) return

        when (ipMode) {
            IpCallMode.PREFIX -> {
                // If prefix is set, prepend it (e.g. 096 + 017...)
                val target = if (ipPrefix.isNotBlank() && !clean.startsWith(ipPrefix)) {
                    ipPrefix + clean
                } else {
                    clean
                }
                CallManager.initiateCall(context, target, simSlot)
            }

            IpCallMode.SIP -> {
                val sipUri = if (ipGateway.isNotBlank() && !clean.contains("@")) {
                    Uri.parse("sip:$clean@$ipGateway")
                } else {
                    Uri.parse("sip:$clean")
                }
                try {
                    val intent = Intent(Intent.ACTION_CALL, sipUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallback = Intent(Intent.ACTION_DIAL, sipUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                }
            }

            IpCallMode.APP_CHOOSER -> {
                val uri = Uri.parse("tel:$clean")
                val intent = Intent(Intent.ACTION_CALL, uri)
                val chooser = Intent.createChooser(intent, "Choose IP Calling App").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    CallManager.initiateCall(context, clean, simSlot)
                }
            }
        }
    }
}
