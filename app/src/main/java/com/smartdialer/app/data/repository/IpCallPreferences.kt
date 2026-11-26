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

data class SipAccountProfile(
    val id: String,
    val label: String,
    val username: String,
    val password: String,
    val domain: String,
    val outboundProxy: String = "",
    val displayName: String = "",
    val authName: String = "",
    val voicemail: String = "",
    val transportProtocol: String = "UDP",
    val verifyTls: Boolean = false,
    val enableStun: Boolean = false,
    val stunServer: String = "",
    val stunPort: String = "3478",
    val enableSrtp: Boolean = false,
    val ipPrefix: String = "",
    val useMobileData: Boolean = true
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("label", label)
        put("username", username)
        put("password", password)
        put("domain", domain)
        put("outboundProxy", outboundProxy)
        put("displayName", displayName)
        put("authName", authName)
        put("voicemail", voicemail)
        put("transportProtocol", transportProtocol)
        put("verifyTls", verifyTls)
        put("enableStun", enableStun)
        put("stunServer", stunServer)
        put("stunPort", stunPort)
        put("enableSrtp", enableSrtp)
        put("ipPrefix", ipPrefix)
        put("useMobileData", useMobileData)
    }

    companion object {
        fun fromJson(obj: org.json.JSONObject): SipAccountProfile = SipAccountProfile(
            id = obj.optString("id", "sip_${System.currentTimeMillis()}"),
            label = obj.optString("label", "SIP Account"),
            username = obj.optString("username", ""),
            password = obj.optString("password", ""),
            domain = obj.optString("domain", ""),
            outboundProxy = obj.optString("outboundProxy", ""),
            displayName = obj.optString("displayName", ""),
            authName = obj.optString("authName", ""),
            voicemail = obj.optString("voicemail", ""),
            transportProtocol = obj.optString("transportProtocol", "UDP"),
            verifyTls = obj.optBoolean("verifyTls", false),
            enableStun = obj.optBoolean("enableStun", false),
            stunServer = obj.optString("stunServer", ""),
            stunPort = obj.optString("stunPort", "3478"),
            enableSrtp = obj.optBoolean("enableSrtp", false),
            ipPrefix = obj.optString("ipPrefix", ""),
            useMobileData = obj.optBoolean("useMobileData", true)
        )
    }
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

    // Real Account Balance (BDT)
    var balance: Double
        get() = prefs.getString("sip_balance", "0.00")?.toDoubleOrNull() ?: 0.00
        set(value) {
            prefs.edit().putString("sip_balance", String.format(java.util.Locale.US, "%.2f", value)).apply()
        }

    var isRealBalanceFetched: Boolean
        get() = prefs.getBoolean("sip_real_balance_fetched", false)
        set(value) = prefs.edit().putBoolean("sip_real_balance_fetched", value).apply()

    val isConfigured: Boolean
        get() = username.isNotBlank() || domain.isNotBlank() || ipPrefix.isNotBlank()

    // ==========================================
    // IP Call Sound & Audio Quality Settings
    // ==========================================
    // Audio Input: VOICE_COMMUNICATION, MIC, VOICE_RECOGNITION, CAMCORDER, UNPROCESSED
    var audioInputSource: String
        get() = prefs.getString("sip_audio_input_source", "VOICE_COMMUNICATION") ?: "VOICE_COMMUNICATION"
        set(value) = prefs.edit().putString("sip_audio_input_source", value).apply()

    // Quality Preset: STANDARD_8K (Standard 8kHz), HD_16K (720p HD 16kHz), FHD_24K (1080p FHD 24kHz), ULTRA_48K (4K Ultra HD 48kHz Hi-Fi)
    var audioQualityPreset: String
        get() = prefs.getString("sip_audio_quality_preset", "HD_16K") ?: "HD_16K"
        set(value) = prefs.edit().putString("sip_audio_quality_preset", value).apply()

    // Preferred Codec: AUTO, PCMA (G.711 A-law 8), PCMU (G.711 u-law 0)
    var preferredCodec: String
        get() = prefs.getString("sip_preferred_codec", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("sip_preferred_codec", value).apply()

    // Hardware Audio Processing & Mic Boost
    var enableAec: Boolean
        get() = prefs.getBoolean("sip_enable_aec", true)
        set(value) = prefs.edit().putBoolean("sip_enable_aec", value).apply()

    var enableNs: Boolean
        get() = prefs.getBoolean("sip_enable_ns", true)
        set(value) = prefs.edit().putBoolean("sip_enable_ns", value).apply()

    var enableAgc: Boolean
        get() = prefs.getBoolean("sip_enable_agc", true)
        set(value) = prefs.edit().putBoolean("sip_enable_agc", value).apply()

    // Mic Gain Boost in dB: 0, 3, 6, 12
    var micGainBoost: Int
        get() = prefs.getInt("sip_mic_gain_boost", 0)
        set(value) = prefs.edit().putInt("sip_mic_gain_boost", value).apply()

    // SRTP Media Encryption (RFC 3711 AES_CM_128_HMAC_SHA1_80)
    var enableSrtp: Boolean
        get() = prefs.getBoolean("sip_enable_srtp", false)
        set(value) = prefs.edit().putBoolean("sip_enable_srtp", value).apply()

    // Smart Audio Routing: Headset-Only Mode
    var headsetOnlyMode: Boolean
        get() = prefs.getBoolean("sip_headset_only_mode", false)
        set(value) = prefs.edit().putBoolean("sip_headset_only_mode", value).apply()

    // Multi-SIP Account Management
    var activeAccountId: String
        get() = prefs.getString("sip_active_account_id", "") ?: ""
        set(value) = prefs.edit().putString("sip_active_account_id", value).apply()

    fun getSavedAccounts(): List<SipAccountProfile> {
        val raw = prefs.getString("sip_multi_accounts_json", null)
        val list = mutableListOf<SipAccountProfile>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(SipAccountProfile.fromJson(it)) }
                }
            } catch (_: Exception) {
            }
        }
        if (list.isEmpty() && (username.isNotBlank() || domain.isNotBlank())) {
            val initial = snapshotCurrentAsProfile("default_1", displayName.ifBlank { username.ifBlank { domain } })
            list.add(initial)
            saveAccountList(list)
            activeAccountId = initial.id
        }
        return list
    }

    fun snapshotCurrentAsProfile(id: String = activeAccountId.ifBlank { "sip_${System.currentTimeMillis()}" }, label: String = ""): SipAccountProfile {
        return SipAccountProfile(
            id = id,
            label = label.ifBlank { displayName.ifBlank { username.ifBlank { domain.ifBlank { "SIP Account" } } } },
            username = username,
            password = password,
            domain = domain,
            outboundProxy = outboundProxy,
            displayName = displayName,
            authName = authName,
            voicemail = voicemail,
            transportProtocol = transportProtocol,
            verifyTls = verifyTls,
            enableStun = enableStun,
            stunServer = stunServer,
            stunPort = stunPort,
            enableSrtp = enableSrtp,
            ipPrefix = ipPrefix,
            useMobileData = useMobileData
        )
    }

    fun saveAccountProfile(profile: SipAccountProfile, makeActive: Boolean = true) {
        val existing = getSavedAccounts().toMutableList()
        val idx = existing.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            existing[idx] = profile
        } else {
            existing.add(profile)
        }
        saveAccountList(existing)
        if (makeActive) {
            applyProfileAsActive(profile)
        }
    }

    fun deleteAccountProfile(id: String) {
        val remaining = getSavedAccounts().filterNot { it.id == id }
        saveAccountList(remaining)
        if (activeAccountId == id) {
            if (remaining.isNotEmpty()) {
                applyProfileAsActive(remaining.first())
            } else {
                activeAccountId = ""
            }
        }
    }

    fun switchActiveAccount(id: String): SipAccountProfile? {
        val target = getSavedAccounts().firstOrNull { it.id == id } ?: return null
        applyProfileAsActive(target)
        return target
    }

    fun replaceAllAccounts(accounts: List<SipAccountProfile>, activeId: String) {
        saveAccountList(accounts)
        val active = accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
        if (active != null) {
            applyProfileAsActive(active)
        }
    }

    private fun applyProfileAsActive(profile: SipAccountProfile) {
        activeAccountId = profile.id
        username = profile.username
        password = profile.password
        domain = profile.domain
        outboundProxy = profile.outboundProxy
        displayName = profile.displayName
        authName = profile.authName
        voicemail = profile.voicemail
        transportProtocol = profile.transportProtocol
        verifyTls = profile.verifyTls
        enableStun = profile.enableStun
        stunServer = profile.stunServer
        stunPort = profile.stunPort
        enableSrtp = profile.enableSrtp
        ipPrefix = profile.ipPrefix
        useMobileData = profile.useMobileData
    }

    private fun saveAccountList(list: List<SipAccountProfile>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sip_multi_accounts_json", arr.toString()).apply()
    }

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
