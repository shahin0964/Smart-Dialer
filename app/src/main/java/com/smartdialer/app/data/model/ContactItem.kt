package com.smartdialer.app.data.model

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val lookupKey: String? = null
) {
    val initial: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "#"
}
