package com.smartdialer.app.data.repository

import android.content.Context
import android.content.SharedPreferences

class AppSettingsPreferences(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_dialer_app_settings", Context.MODE_PRIVATE)

    // ==========================================
    // 1. Dual SIM Settings
    // ==========================================
    // -1 = Always Ask, 0 = SIM 1, 1 = SIM 2
    var preferredSimSlot: Int
        get() = prefs.getInt("preferred_sim_slot", -1)
        set(value) = prefs.edit().putInt("preferred_sim_slot", value).apply()

    var askEveryTime: Boolean
        get() = prefs.getBoolean("sim_ask_every_time", true)
        set(value) = prefs.edit().putBoolean("sim_ask_every_time", value).apply()

    // ==========================================
    // 2. Call Recording Settings
    // ==========================================
    var autoRecord: Boolean
        get() = prefs.getBoolean("call_record_auto", false)
        set(value) = prefs.edit().putBoolean("call_record_auto", value).apply()

    var recordUnknownOnly: Boolean
        get() = prefs.getBoolean("call_record_unknown_only", false)
        set(value) = prefs.edit().putBoolean("call_record_unknown_only", value).apply()

    var audioFormat: String
        get() = prefs.getString("call_record_format", "AAC / M4A") ?: "AAC / M4A"
        set(value) = prefs.edit().putString("call_record_format", value).apply()

    // ==========================================
    // 3. Sounds & Haptics Settings
    // ==========================================
    var dialpadTones: Boolean
        get() = prefs.getBoolean("sound_dialpad_tones", true)
        set(value) = prefs.edit().putBoolean("sound_dialpad_tones", value).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean("sound_dialpad_vibration", true)
        set(value) = prefs.edit().putBoolean("sound_dialpad_vibration", value).apply()

    var callConnectedVibration: Boolean
        get() = prefs.getBoolean("sound_call_connected_vibration", true)
        set(value) = prefs.edit().putBoolean("sound_call_connected_vibration", value).apply()

    var callEndedVibration: Boolean
        get() = prefs.getBoolean("sound_call_ended_vibration", true)
        set(value) = prefs.edit().putBoolean("sound_call_ended_vibration", value).apply()

    // ==========================================
    // 4. Blocked Numbers & 3-Layer Spam Protection
    // ==========================================
    var blockUnknownNumbers: Boolean
        get() = prefs.getBoolean("block_unknown_numbers", false)
        set(value) = prefs.edit().putBoolean("block_unknown_numbers", value).apply()

    var spamProtectionEnabled: Boolean
        get() = prefs.getBoolean("spam_protection_enabled", true)
        set(value) = prefs.edit().putBoolean("spam_protection_enabled", value).apply()

    var spamProtectionLevel: com.smartdialer.app.data.model.SpamProtectionLevel
        get() = com.smartdialer.app.data.model.SpamProtectionLevel.fromId(
            prefs.getString("spam_protection_layer", com.smartdialer.app.data.model.SpamProtectionLevel.SMART.id)
        )
        set(value) = prefs.edit().putString("spam_protection_layer", value.id).apply()

    var silenceUnknownCallers: Boolean
        get() = prefs.getBoolean("silence_unknown_callers", false)
        set(value) = prefs.edit().putBoolean("silence_unknown_callers", value).apply()

    var blockedNumbers: Set<String>
        get() = prefs.getStringSet("blocked_numbers_set", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("blocked_numbers_set", value).apply()

    var reportedSpamNumbers: Set<String>
        get() = prefs.getStringSet("reported_spam_numbers_set", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("reported_spam_numbers_set", value).apply()

    fun addBlockedNumber(number: String) {
        val clean = number.trim()
        if (clean.isNotBlank()) {
            val updated = blockedNumbers.toMutableSet().apply { add(clean) }
            blockedNumbers = updated
        }
    }

    fun removeBlockedNumber(number: String) {
        val clean = number.trim()
        val updated = blockedNumbers.toMutableSet().apply { remove(clean) }
        blockedNumbers = updated
    }

    fun reportSpam(number: String) {
        val clean = number.trim()
        if (clean.isNotBlank()) {
            val updatedSpam = reportedSpamNumbers.toMutableSet().apply { add(clean) }
            reportedSpamNumbers = updatedSpam
            addBlockedNumber(clean)
        }
    }

    fun unreportSpam(number: String) {
        val clean = number.trim()
        val updatedSpam = reportedSpamNumbers.toMutableSet().apply { remove(clean) }
        reportedSpamNumbers = updatedSpam
        removeBlockedNumber(clean)
    }

    fun isReportedSpam(number: String): Boolean {
        val clean = number.trim().replace(" ", "").replace("-", "")
        if (clean.isBlank()) return false
        return reportedSpamNumbers.any { reported ->
            val rClean = reported.trim().replace(" ", "").replace("-", "")
            clean.endsWith(rClean) || rClean.endsWith(clean)
        }
    }

    fun isBlocked(number: String): Boolean {
        val clean = number.trim().replace(" ", "").replace("-", "")
        if (clean.isBlank()) return false
        if (blockUnknownNumbers && clean.all { it.isDigit() } && clean.length < 5) return true
        return blockedNumbers.any { blocked ->
            val bClean = blocked.trim().replace(" ", "").replace("-", "")
            clean.endsWith(bClean) || bClean.endsWith(clean)
        }
    }
}
