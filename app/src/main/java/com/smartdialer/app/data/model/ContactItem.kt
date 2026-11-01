package com.smartdialer.app.data.model

data class ContactAccountInfo(
    val name: String? = null,   // e.g. "shahin0964@gmail.com"
    val type: String? = null    // e.g. "com.google"
) {
    val isGoogle: Boolean
        get() = type?.equals("com.google", ignoreCase = true) == true ||
                name?.contains("@gmail.com", ignoreCase = true) == true ||
                name?.contains("@googlemail.com", ignoreCase = true) == true
}

enum class ContactSourceFilter(val displayName: String) {
    ALL("All Contacts"),
    GOOGLE("Google / Gmail"),
    DEVICE("Phone Storage"),
    SIM("SIM Card")
}

data class ContactCounts(
    val total: Int = 0,
    val google: Int = 0,
    val device: Int = 0,
    val sim: Int = 0
)

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String, // primary phone number
    val phoneNumbers: List<String> = emptyList(), // all phone numbers
    val email: String? = null, // primary email
    val emails: List<String> = emptyList(), // all emails
    val photoUri: String? = null,
    val lookupKey: String? = null,
    val accountType: String? = null,
    val accountName: String? = null,
    val isGoogleContact: Boolean = false
) {
    val initial: String
        get() {
            val trimmed = name.trim()
            return if (trimmed.isNotEmpty() && trimmed.first().isLetter()) {
                trimmed.first().uppercase()
            } else {
                "#"
            }
        }

    val displaySubtitle: String
        get() = when {
            phoneNumber.isNotBlank() -> phoneNumber
            !email.isNullOrBlank() -> email
            else -> "No phone number"
        }
}
