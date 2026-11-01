package com.smartdialer.app.data.repository

import android.content.Context
import com.smartdialer.app.data.model.SpamAction
import com.smartdialer.app.data.model.SpamCheckResult
import com.smartdialer.app.data.model.SpamProtectionLevel
import com.smartdialer.app.data.model.SpamRiskLevel

/**
 * Intelligent 3-Layer Spam Protection Engine.
 * Evaluates incoming callers, call log entries, and dialer queries against
 * user blocklists, user spam reports, known telemarketer prefixes, and suspicious spoofed patterns.
 */
object SpamProtectionManager {

    // Known automated telemarketing / robo-call prefix patterns
    private val KNOWN_SPAM_PREFIXES = listOf(
        "+00", "+01", "+999", "+882", "+883",
        "1800", "+1800", "1888", "+1888", "1877", "+1877",
        "1866", "+1866", "1855", "+1855", "1844", "+1844"
    )

    fun checkNumber(
        context: Context,
        rawNumber: String,
        contactName: String? = null
    ): SpamCheckResult {
        val appSettings = AppSettingsPreferences(context)
        val cleanNumber = rawNumber.trim().replace(" ", "").replace("-", "")

        // 1. Check if user explicitly blocked this number
        if (appSettings.isBlocked(cleanNumber)) {
            val action = when (appSettings.spamProtectionLevel) {
                SpamProtectionLevel.BASIC -> SpamAction.WARN
                SpamProtectionLevel.SMART -> SpamAction.REJECT
                SpamProtectionLevel.MAXIMUM -> SpamAction.REJECT
            }
            return SpamCheckResult(
                isSpam = true,
                isBlocked = true,
                riskLevel = SpamRiskLevel.BLOCKED,
                category = "Blocked by You",
                reason = "This number is in your personal blocked list",
                action = action
            )
        }

        // 2. Check if user previously reported this number as spam
        if (appSettings.isReportedSpam(cleanNumber)) {
            val action = when (appSettings.spamProtectionLevel) {
                SpamProtectionLevel.BASIC -> SpamAction.WARN
                SpamProtectionLevel.SMART -> SpamAction.SILENCE
                SpamProtectionLevel.MAXIMUM -> SpamAction.REJECT
            }
            return SpamCheckResult(
                isSpam = true,
                isBlocked = false,
                riskLevel = SpamRiskLevel.HIGH,
                category = "Reported Spam",
                reason = "Marked as spam based on your report",
                action = action
            )
        }

        // If master spam protection is disabled, do not flag further
        if (!appSettings.spamProtectionEnabled) {
            return SpamCheckResult()
        }

        // 3. Check for Hidden / Private / Unknown caller IDs
        val isPrivateOrHidden = cleanNumber.isBlank() ||
                cleanNumber.equals("private", ignoreCase = true) ||
                cleanNumber.equals("unknown", ignoreCase = true) ||
                cleanNumber.equals("restricted", ignoreCase = true)

        if (isPrivateOrHidden) {
            if (appSettings.blockUnknownNumbers || appSettings.silenceUnknownCallers) {
                val action = if (appSettings.spamProtectionLevel == SpamProtectionLevel.MAXIMUM) {
                    SpamAction.REJECT
                } else {
                    SpamAction.SILENCE
                }
                return SpamCheckResult(
                    isSpam = true,
                    isBlocked = false,
                    riskLevel = SpamRiskLevel.MEDIUM,
                    category = "Private / Hidden Caller",
                    reason = "Caller ID is hidden or private",
                    action = action
                )
            }
        }

        // 4. If this is a known, saved contact, do not flag as spam (safe contact override)
        if (!contactName.isNullOrBlank() && contactName != "Unknown" && contactName != rawNumber) {
            return SpamCheckResult()
        }

        // 5. Pattern & Telemarketing Detection
        var detectedCategory: String? = null
        var detectedRisk = SpamRiskLevel.NONE

        for (prefix in KNOWN_SPAM_PREFIXES) {
            if (cleanNumber.startsWith(prefix)) {
                detectedCategory = "Robocall / Telemarketer"
                detectedRisk = SpamRiskLevel.HIGH
                break
            }
        }

        // Suspicious short invalid digit sequences (e.g. 3-4 digits that aren't emergency services)
        if (detectedCategory == null && cleanNumber.length in 3..5 && cleanNumber.all { it.isDigit() }) {
            val safeShortCodes = listOf("999", "911", "112", "199", "100", "101", "102")
            if (!safeShortCodes.contains(cleanNumber)) {
                detectedCategory = "Automated Service / Promo"
                detectedRisk = SpamRiskLevel.MEDIUM
            }
        }

        // Check if strict maximum mode silences/screens unsaved callers
        if (detectedCategory == null && appSettings.spamProtectionLevel == SpamProtectionLevel.MAXIMUM) {
            if (appSettings.silenceUnknownCallers) {
                return SpamCheckResult(
                    isSpam = false,
                    isBlocked = false,
                    riskLevel = SpamRiskLevel.LOW,
                    category = "Unknown Caller (Screened)",
                    reason = "Caller is not in your contacts (Maximum strict filter)",
                    action = SpamAction.SILENCE
                )
            }
        }

        if (detectedCategory != null) {
            val action = when (appSettings.spamProtectionLevel) {
                SpamProtectionLevel.BASIC -> SpamAction.WARN
                SpamProtectionLevel.SMART -> SpamAction.SILENCE
                SpamProtectionLevel.MAXIMUM -> SpamAction.REJECT
            }
            return SpamCheckResult(
                isSpam = true,
                isBlocked = false,
                riskLevel = detectedRisk,
                category = detectedCategory,
                reason = "Matched spam/telemarketing pattern ($detectedCategory)",
                action = action
            )
        }

        return SpamCheckResult()
    }
}
