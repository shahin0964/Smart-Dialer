package com.smartdialer.app.data.callerid

import android.content.Context
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.repository.AppSettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class CallerIdResult(
    val phoneNumber: String,
    val name: String?,
    val carrier: String,
    val location: String,
    val isSpam: Boolean = false,
    val spamCategory: String? = null,
    val spamCount: Int = 0,
    val isVerified: Boolean = false,
    val source: String = "Smart Lookup",
    val photoUrl: String? = null
)

class CallerIdService(private val context: Context) {
    private val appSettings = AppSettingsPreferences(context)
    private val cache = ConcurrentHashMap<String, CallerIdResult>()

    /**
     * Looks up caller ID information for any phone number.
     * Evaluates in-memory cache, operator/carrier, verified directory, local contacts,
     * user spam reports, and real-time online search.
     */
    suspend fun lookup(
        rawNumber: String,
        localContacts: List<ContactItem>? = null
    ): CallerIdResult = withContext(Dispatchers.IO) {
        val clean = rawNumber.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        if (clean.isBlank()) {
            return@withContext CallerIdResult(rawNumber, null, "Unknown", "Unknown")
        }

        // 1. Check in-memory cache for instant response
        cache[clean]?.let { return@withContext it }

        // 2. Carrier & Location detection (Instant 0ms, 100% reliable)
        val carrierInfo = TelecomCarrierDetector.detect(clean)

        // 3. Check if number is in phone contacts
        val matchedContact = localContacts?.firstOrNull { c ->
            c.phoneNumbers.any { normalizeDigits(it) == normalizeDigits(clean) } ||
                normalizeDigits(c.phoneNumber) == normalizeDigits(clean)
        }

        if (matchedContact != null) {
            val res = CallerIdResult(
                phoneNumber = clean,
                name = matchedContact.name,
                carrier = carrierInfo.carrierName,
                location = carrierInfo.location,
                isSpam = false,
                isVerified = true,
                source = "Phone Contacts",
                photoUrl = matchedContact.photoUri
            )
            cache[clean] = res
            return@withContext res
        }

        // 4. Check verified national directory / emergency helplines
        val directoryName = DirectoryDatabase.lookup(clean)
        if (directoryName != null) {
            val res = CallerIdResult(
                phoneNumber = clean,
                name = directoryName,
                carrier = carrierInfo.carrierName,
                location = "Bangladesh (Official Helpline)",
                isSpam = false,
                isVerified = true,
                source = "Verified Directory"
            )
            cache[clean] = res
            return@withContext res
        }

        // 5. Check local user spam list
        val isUserReportedSpam = appSettings.reportedSpamNumbers.contains(clean) ||
            appSettings.blockedNumbers.contains(clean)

        // 6. Online Real-Time Truecaller / Gateway search
        val onlineResult = lookupOnline(clean)

        val finalName = onlineResult?.name
        val isSpam = isUserReportedSpam || (onlineResult?.isSpam == true)
        val spamCategory = if (isSpam) (onlineResult?.spamCategory ?: "Reported Spam") else null
        val source = onlineResult?.source ?: if (finalName != null) "Truecaller / Live Search" else "Operator Identification"

        val result = CallerIdResult(
            phoneNumber = clean,
            name = finalName,
            carrier = onlineResult?.carrier ?: carrierInfo.carrierName,
            location = onlineResult?.location ?: carrierInfo.location,
            isSpam = isSpam,
            spamCategory = spamCategory,
            spamCount = onlineResult?.spamCount ?: (if (isUserReportedSpam) 1 else 0),
            isVerified = onlineResult?.isVerified ?: false,
            source = source,
            photoUrl = onlineResult?.photoUrl
        )

        cache[clean] = result
        result
    }

    private fun lookupOnline(phoneNumber: String): CallerIdResult? {
        try {
            val intlNumber = toInternationalFormat(phoneNumber)

            // 1. If user configured custom Truecaller Gateway URL
            val customEndpoint = appSettings.truecallerCustomEndpoint?.trim()
            val token = appSettings.truecallerAuthToken?.trim()

            if (!customEndpoint.isNullOrBlank()) {
                val url = URL("$customEndpoint?phone=$intlNumber")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    return parseJsonCallerId(json, phoneNumber)
                }
            }

            // 2. Default Public Reverse Lookup Service
            val lookupUrl = URL("https://caller-id-api.smartdialer.workers.dev/api/lookup?phone=$intlNumber")
            val conn = (lookupUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmartDialer-Android")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                return parseJsonCallerId(json, phoneNumber)
            }
        } catch (e: Exception) {
            // Silently fall back to offline carrier identification
        }
        return null
    }

    private fun parseJsonCallerId(json: JSONObject, phone: String): CallerIdResult {
        val name = json.optString("name").takeIf { it.isNotBlank() && it != "null" }
        val carrier = json.optString("carrier", json.optString("operator")).takeIf { it.isNotBlank() }
            ?: TelecomCarrierDetector.detect(phone).carrierName
        val location = json.optString("location", json.optString("city")).takeIf { it.isNotBlank() } ?: "Bangladesh"
        val isSpam = json.optBoolean("isSpam", false) || json.optInt("spamScore", 0) > 3
        val spamCategory = json.optString("spamCategory", if (isSpam) "Spam / Telemarketer" else null)
        val photoUrl = json.optString("image", json.optString("avatar")).takeIf { it.isNotBlank() && it != "null" }

        return CallerIdResult(
            phoneNumber = phone,
            name = name,
            carrier = carrier,
            location = location,
            isSpam = isSpam,
            spamCategory = spamCategory,
            isVerified = json.optBoolean("isVerified", false),
            source = "Truecaller Live Data",
            photoUrl = photoUrl
        )
    }

    private fun toInternationalFormat(phone: String): String {
        val clean = phone.replace("+", "").replace(" ", "").replace("-", "")
        return when {
            clean.startsWith("880") -> clean
            clean.startsWith("01") -> "880" + clean.removePrefix("0")
            else -> clean
        }
    }

    private fun normalizeDigits(raw: String): String {
        return raw.filter { it.isDigit() }.takeLast(10)
    }
}
