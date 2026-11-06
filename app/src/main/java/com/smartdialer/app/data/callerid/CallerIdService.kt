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
     * user spam reports, and real-time online Truecaller search.
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

        // 4. Check verified national directory / emergency helplines & banks
        val directoryName = DirectoryDatabase.lookup(clean)
        if (directoryName != null) {
            val res = CallerIdResult(
                phoneNumber = clean,
                name = directoryName,
                carrier = carrierInfo.carrierName,
                location = "Bangladesh (Verified Organization)",
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

        // 6. Online Real-Time Truecaller / Gateway search (if enabled)
        val onlineResult = if (appSettings.callerIdEnabled) {
            lookupOnline(clean)
        } else null

        val finalName = onlineResult?.name
        val isSpam = isUserReportedSpam || (onlineResult?.isSpam == true)
        val spamCategory = if (isSpam) (onlineResult?.spamCategory ?: "Reported Spam") else null
        val source = onlineResult?.source ?: if (finalName != null) "Truecaller Live Data" else "Operator Identification"

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
        val intlNumber = toInternationalFormat(phoneNumber)
        val token = appSettings.truecallerAuthToken?.trim()
        val customEndpoint = appSettings.truecallerCustomEndpoint?.trim()

        // 1. Direct Truecaller v2 API query if user configured auth token
        if (!token.isNullOrBlank()) {
            try {
                val directUrl = URL("https://search5-noneu.truecaller.com/v2/search?q=%2B$intlNumber&countryCode=BD&type=4&locAddr=&placement=SEARCHRESULTS,HISTORY,DETAILS&encoding=json")
                val conn = (directUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Truecaller/11.75.5 (Android;10)")
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val parsed = parseJsonCallerId(json, phoneNumber)
                    if (parsed.name != null) return parsed
                }
            } catch (e: Exception) {
                // Ignore and fall through
            }
        }

        // 2. Custom Gateway URL if configured by user
        if (!customEndpoint.isNullOrBlank()) {
            try {
                val separator = if (customEndpoint.contains("?")) "&" else "?"
                val url = URL("$customEndpoint${separator}phone=$intlNumber&q=$intlNumber")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "SmartDialer-Android")
                    if (!token.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val parsed = parseJsonCallerId(json, phoneNumber)
                    if (parsed.name != null) return parsed
                }
            } catch (e: Exception) {
                // Ignore and fall through
            }
        }

        return null
    }

    private fun parseJsonCallerId(json: JSONObject, phone: String): CallerIdResult {
        // 1. Official Truecaller v2 format with "data" array
        val dataArray = json.optJSONArray("data")
        if (dataArray != null && dataArray.length() > 0) {
            val item = dataArray.getJSONObject(0)
            val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" }
            val image = item.optString("image").takeIf { it.isNotBlank() && it != "null" }

            var carrier: String? = null
            val phones = item.optJSONArray("phones")
            if (phones != null && phones.length() > 0) {
                carrier = phones.getJSONObject(0).optString("carrier").takeIf { it.isNotBlank() }
            }

            var location: String? = null
            val addresses = item.optJSONArray("addresses")
            if (addresses != null && addresses.length() > 0) {
                val addr = addresses.getJSONObject(0)
                val city = addr.optString("city").takeIf { it.isNotBlank() }
                val country = addr.optString("countryCode").takeIf { it.isNotBlank() }
                location = when {
                    city != null && country != null -> "$city, $country"
                    city != null -> city
                    country != null -> country
                    else -> null
                }
            }

            val badges = item.optJSONArray("badges")?.toString() ?: ""
            val isVerified = badges.contains("verified")
            val isSpam = item.optDouble("score", 1.0) < 0.4 || badges.contains("spam") || badges.contains("fraud")

            return CallerIdResult(
                phoneNumber = phone,
                name = name,
                carrier = carrier ?: TelecomCarrierDetector.detect(phone).carrierName,
                location = location ?: TelecomCarrierDetector.detect(phone).location,
                isSpam = isSpam,
                spamCategory = if (isSpam) "Reported Spam / Telemarketer" else null,
                isVerified = isVerified,
                source = "Truecaller Live Data",
                photoUrl = image
            )
        }

        // 2. Flat JSON format (from custom Gateway or proxy)
        val name = json.optString("name").takeIf { it.isNotBlank() && it != "null" }
        val carrier = json.optString("carrier", json.optString("operator")).takeIf { it.isNotBlank() }
            ?: TelecomCarrierDetector.detect(phone).carrierName
        val location = json.optString("location", json.optString("city")).takeIf { it.isNotBlank() }
            ?: TelecomCarrierDetector.detect(phone).location
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
            source = if (name != null) "Truecaller Live Data" else "Operator Identification",
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
