package com.smartdialer.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.smartdialer.app.telecom.CallManager

enum class IpCallMode {
    SIP,            // Standard SIP URI (sip:number@domain)
    PREFIX,         // Prepend IP prefix/gateway to destination number (e.g. 096 + number)
    APP_CHOOSER     // Open installed IP / VoIP calling apps (PortSIP, Alaap, etc.)
}

class IpCallPreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_dialer_ip_prefs", Context.MODE_PRIVATE)

    // Basic Account Settings (PortSIP Main Screen)
    var username: String
        get() = prefs.getString("sip_username", prefs.getString("ip_number", "") ?: "") ?: ""
        set(value) {
            prefs.edit().putString("sip_username", value.trim()).putString("ip_number", value.trim()).apply()
        }

    var ipNumber: String
        get() = username
        set(value) { username = value }

    var password: String
        get() = prefs.getString("sip_password", "") ?: ""
        set(value) = prefs.edit().putString("sip_password", value).apply()

    var domain: String
        get() = prefs.getString("sip_domain", prefs.getString("ip_gateway", "") ?: "") ?: ""
        set(value) {
            prefs.edit().putString("sip_domain", value.trim()).putString("ip_gateway", value.trim()).apply()
        }

    var ipGateway: String
        get() = domain
        set(value) { domain = value }

    // Advanced Account Settings (PortSIP Advanced Screen)
    var outboundProxy: String
        get() = prefs.getString("sip_outbound_proxy", "") ?: ""
        set(value) = prefs.edit().putString("sip_outbound_proxy", value.trim()).apply()

    var displayName: String
        get() = prefs.getString("sip_display_name", "") ?: ""
        set(value) = prefs.edit().putString("sip_display_name", value.trim()).apply()

    var authName: String
        get() = prefs.getString("sip_auth_name", "") ?: ""
        set(value) = prefs.edit().putString("sip_auth_name", value.trim()).apply()

    var voicemail: String
        get() = prefs.getString("sip_voicemail", "") ?: ""
        set(value) = prefs.edit().putString("sip_voicemail", value.trim()).apply()

    // Transport Protocol: UDP, TLS, TCP (UDP default)
    var transportProtocol: String
        get() = prefs.getString("sip_transport", "UDP") ?: "UDP"
        set(value) = prefs.edit().putString("sip_transport", value).apply()

    // TLS Certificate Management
    var verifyTls: Boolean
        get() = prefs.getBoolean("sip_verify_tls", false)
        set(value) = prefs.edit().putBoolean("sip_verify_tls", value).apply()

    // STUN Settings
    var enableStun: Boolean
        get() = prefs.getBoolean("sip_enable_stun", false)
        set(value) = prefs.edit().putBoolean("sip_enable_stun", value).apply()

    var stunServer: String
        get() = prefs.getString("sip_stun_server", "") ?: ""
        set(value) = prefs.edit().putString("sip_stun_server", value.trim()).apply()

    var stunPort: String
        get() = prefs.getString("sip_stun_port", "3478") ?: "3478"
        set(value) = prefs.edit().putString("sip_stun_port", value.trim()).apply()

    // Mobile Network & Data (Default ON)
    var useMobileData: Boolean
        get() = prefs.getBoolean("sip_use_mobile_data", true)
        set(value) = prefs.edit().putBoolean("sip_use_mobile_data", value).apply()

    // Logging
    var enableLogging: Boolean
        get() = prefs.getBoolean("sip_enable_logging", false)
        set(value) = prefs.edit().putBoolean("sip_enable_logging", value).apply()

    // Dialing Prefix & Mode
    var ipPrefix: String
        get() = prefs.getString("ip_prefix", "") ?: ""
        set(value) = prefs.edit().putString("ip_prefix", value.trim()).apply()

    var ipMode: IpCallMode
        get() {
            val name = prefs.getString("ip_mode", IpCallMode.SIP.name) ?: IpCallMode.SIP.name
            return try {
                IpCallMode.valueOf(name)
            } catch (e: Exception) {
                IpCallMode.SIP
            }
        }
        set(value) = prefs.edit().putString("ip_mode", value.name).apply()

    val isConfigured: Boolean
        get() = username.isNotBlank() || domain.isNotBlank() || ipPrefix.isNotBlank()

    fun clearAccount() {
        prefs.edit().clear().apply()
    }

    fun makeIpCall(destinationNumber: String, simSlot: Int = 0) {
        val clean = destinationNumber.trim()
        if (clean.isEmpty()) return

        when (ipMode) {
            IpCallMode.SIP -> {
                val target = if (ipPrefix.isNotBlank() && !clean.startsWith(ipPrefix)) {
                    ipPrefix + clean
                } else {
                    clean
                }
                // In-App direct SIP call via Smart Dialer's own calling engine and UI!
                CallManager.initiateIpCall(context, target)
            }

            IpCallMode.PREFIX -> {
                val target = if (ipPrefix.isNotBlank() && !clean.startsWith(ipPrefix)) {
                    ipPrefix + clean
                } else {
                    clean
                }
                CallManager.initiateCall(context, target, simSlot)
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
