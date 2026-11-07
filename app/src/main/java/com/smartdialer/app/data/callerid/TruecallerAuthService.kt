package com.smartdialer.app.data.callerid

import android.content.Context
import com.smartdialer.app.data.repository.AppSettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

data class TruecallerOtpSession(
    val fullPhoneNumber: String,
    val dialingCode: Int,
    val countryCode: String,
    val significantNumber: String,
    val requestId: String,
    val tokenTtl: Int
)

data class TruecallerLoginResult(
    val installationId: String,
    val userId: Long?,
    val message: String
)

class TruecallerAuthService(private val context: Context) {
    private val appSettings = AppSettingsPreferences(context)

    companion object {
        private const val SEND_OTP_URL = "https://account-asia-south1.truecaller.com/v2/sendOnboardingOtp"
        private const val VERIFY_OTP_URL = "https://account-asia-south1.truecaller.com/v1/verifyOnboardingOtp"
        private const val CLIENT_SECRET = "lvc22mp3l1sfv6ujg83rd17btt"
        private const val USER_AGENT = "Truecaller/11.75.5 (Android;10)"

        private val DEVICE_LIST = listOf(
            Pair("Samsung", "SM-G998B"),
            Pair("Xiaomi", "Redmi Note 10 Pro"),
            Pair("Vivo", "Vivo V21 5G"),
            Pair("OnePlus", "OnePlus 9 Pro"),
            Pair("Realme", "Realme 8 5G"),
            Pair("Oppo", "OPPO Reno6")
        )
    }

    suspend fun sendOtp(rawNumber: String): Result<TruecallerOtpSession> = withContext(Dispatchers.IO) {
        try {
            val parsed = parsePhoneNumber(rawNumber)
                ?: return@withContext Result.failure(Exception("Invalid phone number format. Please check the number."))

            val deviceId = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: (1..16).map { "0123456789abcdef".random() }.joinToString("")
            } catch (e: Exception) {
                (1..16).map { "0123456789abcdef".random() }.joinToString("")
            }

            val manufacturer = android.os.Build.MANUFACTURER.ifBlank { "Samsung" }
            val model = android.os.Build.MODEL.ifBlank { "SM-G998B" }
            val osVersion = android.os.Build.VERSION.RELEASE.ifBlank { "13" }

            val payload = JSONObject().apply {
                put("countryCode", parsed.countryCode)
                put("dialingCode", parsed.dialingCode)
                put("phoneNumber", parsed.significantNumber)
                put("region", "region-2")
                put("sequenceNo", 2)
                put("language", "en")
                put("installationDetails", JSONObject().apply {
                    put("app", JSONObject().apply {
                        put("buildVersion", 5)
                        put("majorVersion", 11)
                        put("minorVersion", 7)
                        put("store", "GOOGLE_PLAY")
                    })
                    put("device", JSONObject().apply {
                        put("deviceId", deviceId)
                        put("language", "en")
                        put("manufacturer", manufacturer)
                        put("model", model)
                        put("osName", "Android")
                        put("osVersion", osVersion)
                        put("mobileServices", JSONArray().put("GMS"))
                    })
                    put("language", "en")
                })
            }

            val url = URL(SEND_OTP_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("clientsecret", CLIENT_SECRET)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val status = json.optInt("status", -1)
                val message = json.optString("message", "")
                val requestId = json.optString("requestId")
                val tokenTtl = json.optInt("tokenTtl", 300)

                // Status 1 or 9 means OTP was truly sent by Truecaller server
                if ((status == 1 || status == 9 || message.equals("Sent", ignoreCase = true)) && requestId.isNotBlank()) {
                    val session = TruecallerOtpSession(
                        fullPhoneNumber = parsed.fullFormatted,
                        dialingCode = parsed.dialingCode,
                        countryCode = parsed.countryCode,
                        significantNumber = parsed.significantNumber,
                        requestId = requestId,
                        tokenTtl = tokenTtl
                    )
                    Result.success(session)
                } else if (status == 3 && json.has("installationId")) {
                    val installationId = json.getString("installationId")
                    appSettings.truecallerAuthToken = installationId
                    appSettings.truecallerPhoneNumber = parsed.fullFormatted
                    appSettings.callerIdEnabled = true
                    Result.success(
                        TruecallerOtpSession(
                            fullPhoneNumber = parsed.fullFormatted,
                            dialingCode = parsed.dialingCode,
                            countryCode = parsed.countryCode,
                            significantNumber = parsed.significantNumber,
                            requestId = "ALREADY_LOGGED_IN",
                            tokenTtl = 0
                        )
                    )
                } else {
                    val errDesc = when (status) {
                        20003 -> "Truecaller সার্ভার অটোমেটিক SMS ব্লক করেছে (Security Error 20003)। অনুগ্রহ করে নিচের 'Truecaller Web Login' বা 'Manual Token' ব্যবহার করুন।"
                        5, 6 -> "খুব বেশিবার চেষ্টা করা হয়েছে (Rate Limit)। অনুগ্রহ করে ১ ঘণ্টা পর আবার চেষ্টা করুন।"
                        else -> if (message.isNotBlank()) "Truecaller: $message" else "Truecaller সার্ভার OTP পাঠায়নি (Status: $status)"
                    }
                    Result.failure(Exception(errDesc))
                }
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                val errMsg = parseErrorMessage(errBody) ?: "Truecaller server rejected request (HTTP $responseCode)"
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(session: TruecallerOtpSession, otpCode: String): Result<TruecallerLoginResult> = withContext(Dispatchers.IO) {
        try {
            val cleanOtp = otpCode.trim().filter { it.isDigit() }
            if (cleanOtp.length < 4) {
                return@withContext Result.failure(Exception("Please enter the complete OTP code sent via SMS."))
            }

            val payload = JSONObject().apply {
                put("countryCode", session.countryCode)
                put("dialingCode", session.dialingCode)
                put("phoneNumber", session.significantNumber)
                put("requestId", session.requestId)
                put("token", cleanOtp)
            }

            val url = URL(VERIFY_OTP_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("clientsecret", CLIENT_SECRET)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val status = json.optInt("status", -1)
                val installationId = json.optString("installationId")
                val userId = if (json.has("userId")) json.optLong("userId") else null

                if (installationId.isNotBlank() && (status == 2 || status == -1)) {
                    appSettings.truecallerAuthToken = installationId
                    appSettings.truecallerPhoneNumber = session.fullPhoneNumber
                    appSettings.callerIdEnabled = true

                    Result.success(
                        TruecallerLoginResult(
                            installationId = installationId,
                            userId = userId,
                            message = "Connected to Truecaller successfully!"
                        )
                    )
                } else if (status == 17) {
                    completeOnboarding(session, cleanOtp)
                } else if (status == 11 || status == 40101) {
                    Result.failure(Exception("ভুল OTP কোড। অনুগ্রহ করে সঠিক কোড দিন।"))
                } else if (status == 7) {
                    Result.failure(Exception("OTP চেষ্টার সীমা শেষ (Limit Exceeded)। অনুগ্রহ করে কিছুক্ষণ পর চেষ্টা করুন।"))
                } else {
                    val msg = json.optString("message", "Verification failed. Please try again.")
                    Result.failure(Exception(msg))
                }
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }
                val errMsg = parseErrorMessage(errBody) ?: "Invalid OTP code or session expired (HTTP $responseCode)"
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun completeOnboarding(session: TruecallerOtpSession, otp: String): Result<TruecallerLoginResult> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("countryCode", session.countryCode)
                put("dialingCode", session.dialingCode)
                put("phoneNumber", session.significantNumber)
                put("requestId", session.requestId)
                put("token", otp)
                put("name", JSONObject().apply {
                    put("first", "Smart")
                    put("last", "Dialer")
                })
            }

            val url = URL("https://account-noneu.truecaller.com/v1/completeOnboarding")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("clientsecret", CLIENT_SECRET)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val instId = json.optString("installationId")
                if (instId.isNotBlank()) {
                    appSettings.truecallerAuthToken = instId
                    appSettings.truecallerPhoneNumber = session.fullPhoneNumber
                    appSettings.callerIdEnabled = true
                    Result.success(TruecallerLoginResult(instId, null, "Connected to Truecaller successfully!"))
                } else {
                    Result.failure(Exception("Could not obtain installationId from signup"))
                }
            } else {
                Result.failure(Exception("Complete signup rejected by server (HTTP ${conn.responseCode})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveToken(token: String, phoneNumber: String? = null) {
        appSettings.truecallerAuthToken = token.trim()
        if (!phoneNumber.isNullOrBlank()) {
            appSettings.truecallerPhoneNumber = phoneNumber.trim()
        }
        appSettings.callerIdEnabled = true
    }

    fun logout() {
        appSettings.truecallerAuthToken = null
        appSettings.truecallerPhoneNumber = null
        appSettings.truecallerUserName = null
    }

    private data class ParsedPhoneNumber(
        val countryCode: String,
        val dialingCode: Int,
        val significantNumber: String,
        val fullFormatted: String
    )

    private fun parsePhoneNumber(raw: String): ParsedPhoneNumber? {
        val digitsOnly = raw.filter { it.isDigit() }
        if (digitsOnly.length < 5) return null

        // Bangladesh handling
        if (raw.startsWith("+880") || raw.startsWith("880")) {
            val sig = digitsOnly.removePrefix("880").removePrefix("0")
            if (sig.length in 9..10) {
                return ParsedPhoneNumber(
                    countryCode = "BD",
                    dialingCode = 880,
                    significantNumber = sig,
                    fullFormatted = "+880$sig"
                )
            }
        } else if (raw.startsWith("01") && digitsOnly.length == 11) {
            val sig = digitsOnly.removePrefix("0")
            return ParsedPhoneNumber(
                countryCode = "BD",
                dialingCode = 880,
                significantNumber = sig,
                fullFormatted = "+880$sig"
            )
        }

        // India handling
        if (raw.startsWith("+91") || (raw.startsWith("91") && digitsOnly.length == 12)) {
            val sig = digitsOnly.removePrefix("91")
            return ParsedPhoneNumber(
                countryCode = "IN",
                dialingCode = 91,
                significantNumber = sig,
                fullFormatted = "+91$sig"
            )
        }

        // USA / Canada handling
        if (raw.startsWith("+1") || (raw.startsWith("1") && digitsOnly.length == 11)) {
            val sig = digitsOnly.removePrefix("1")
            return ParsedPhoneNumber(
                countryCode = "US",
                dialingCode = 1,
                significantNumber = sig,
                fullFormatted = "+1$sig"
            )
        }

        // UK handling
        if (raw.startsWith("+44") || (raw.startsWith("44") && digitsOnly.length >= 11)) {
            val sig = digitsOnly.removePrefix("44").removePrefix("0")
            return ParsedPhoneNumber(
                countryCode = "GB",
                dialingCode = 44,
                significantNumber = sig,
                fullFormatted = "+44$sig"
            )
        }

        // Saudi Arabia handling
        if (raw.startsWith("+966") || (raw.startsWith("966") && digitsOnly.length >= 11)) {
            val sig = digitsOnly.removePrefix("966").removePrefix("0")
            return ParsedPhoneNumber(
                countryCode = "SA",
                dialingCode = 966,
                significantNumber = sig,
                fullFormatted = "+966$sig"
            )
        }

        // UAE handling
        if (raw.startsWith("+971") || (raw.startsWith("971") && digitsOnly.length >= 11)) {
            val sig = digitsOnly.removePrefix("971").removePrefix("0")
            return ParsedPhoneNumber(
                countryCode = "AE",
                dialingCode = 971,
                significantNumber = sig,
                fullFormatted = "+971$sig"
            )
        }

        // Fallback for standard 11-digit local numbers as BD
        if (digitsOnly.length == 11 && digitsOnly.startsWith("0")) {
            val sig = digitsOnly.removePrefix("0")
            return ParsedPhoneNumber(
                countryCode = "BD",
                dialingCode = 880,
                significantNumber = sig,
                fullFormatted = "+880$sig"
            )
        }

        return null
    }

    private fun parseErrorMessage(rawJson: String?): String? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val json = JSONObject(rawJson)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
