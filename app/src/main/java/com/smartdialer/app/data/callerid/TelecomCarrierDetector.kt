package com.smartdialer.app.data.callerid

data class CarrierInfo(
    val carrierName: String,
    val location: String,
    val country: String = "Bangladesh",
    val brandColor: Long = 0xFF1D4ED8
)

object TelecomCarrierDetector {

    /**
     * Resolves the telecom carrier and location for a phone number.
     * Works offline with 0ms latency for all Bangladesh operators and major world codes.
     */
    fun detect(rawNumber: String): CarrierInfo {
        val clean = rawNumber.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        if (clean.isBlank()) {
            return CarrierInfo("Unknown Operator", "Unknown", "Unknown", 0xFF6B7280)
        }

        // Normalize Bangladesh number formats: +8801..., 8801..., 008801..., 01...
        val nationalNumber = when {
            clean.startsWith("+8801") -> "01" + clean.removePrefix("+8801")
            clean.startsWith("8801") -> "01" + clean.removePrefix("8801")
            clean.startsWith("008801") -> "01" + clean.removePrefix("008801")
            clean.startsWith("01") -> clean
            else -> null
        }

        if (nationalNumber != null && nationalNumber.length >= 3) {
            val prefix = nationalNumber.take(3)
            return when (prefix) {
                "017", "013" -> CarrierInfo(
                    carrierName = "Grameenphone (GP)",
                    location = "Bangladesh",
                    brandColor = 0xFF0284C7
                )
                "019", "014" -> CarrierInfo(
                    carrierName = "Banglalink",
                    location = "Bangladesh",
                    brandColor = 0xFFF97316
                )
                "018" -> CarrierInfo(
                    carrierName = "Robi",
                    location = "Bangladesh",
                    brandColor = 0xFFDC2626
                )
                "016" -> CarrierInfo(
                    carrierName = "Airtel",
                    location = "Bangladesh",
                    brandColor = 0xFFE11D48
                )
                "015" -> CarrierInfo(
                    carrierName = "Teletalk",
                    location = "Bangladesh",
                    brandColor = 0xFF059669
                )
                else -> CarrierInfo(
                    carrierName = "Bangladesh Mobile",
                    location = "Bangladesh",
                    brandColor = 0xFF7C3AED
                )
            }
        }

        // International Country Resolution
        if (clean.startsWith("+") || clean.startsWith("00")) {
            val intl = if (clean.startsWith("+")) clean.removePrefix("+") else clean.removePrefix("00")
            return when {
                intl.startsWith("1") -> CarrierInfo("USA / Canada", "North America", "United States", 0xFF1E3A8A)
                intl.startsWith("44") -> CarrierInfo("UK Mobile / Landline", "United Kingdom", "United Kingdom", 0xFF1E40AF)
                intl.startsWith("91") -> CarrierInfo("India Telecom", "India", "India", 0xFFEA580C)
                intl.startsWith("966") -> CarrierInfo("Saudi Arabia Telecom", "Middle East", "Saudi Arabia", 0xFF047857)
                intl.startsWith("971") -> CarrierInfo("UAE Telecom", "Middle East", "UAE", 0xFFB91C1C)
                intl.startsWith("60") -> CarrierInfo("Malaysia Telecom", "Southeast Asia", "Malaysia", 0xFF1D4ED8)
                intl.startsWith("65") -> CarrierInfo("Singapore Telecom", "Southeast Asia", "Singapore", 0xFFDC2626)
                intl.startsWith("974") -> CarrierInfo("Qatar Telecom", "Middle East", "Qatar", 0xFF831843)
                intl.startsWith("965") -> CarrierInfo("Kuwait Telecom", "Middle East", "Kuwait", 0xFF047857)
                intl.startsWith("968") -> CarrierInfo("Oman Telecom", "Middle East", "Oman", 0xFFB45309)
                intl.startsWith("39") -> CarrierInfo("Italy Telecom", "Europe", "Italy", 0xFF15803D)
                else -> CarrierInfo("International Number", "Worldwide", "International", 0xFF6B7280)
            }
        }

        return CarrierInfo("Unknown Operator", "Local", "Unknown", 0xFF6B7280)
    }
}
