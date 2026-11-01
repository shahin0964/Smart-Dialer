package com.smartdialer.app.data.model

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    UNKNOWN
}

data class CallLogItem(
    val id: String,
    val name: String?,
    val phoneNumber: String,
    val type: CallType,
    val dateMillis: Long,
    val durationSeconds: Long,
    val formattedTime: String,
    val formattedDate: String,
    val isToday: Boolean,
    val isYesterday: Boolean,
    val photoUri: String? = null,
    val isSpam: Boolean = false,
    val spamCategory: String? = null
) {
    val displayName: String
        get() = if (!name.isNullOrBlank()) name else phoneNumber

    val initial: String
        get() = displayName.trim().firstOrNull()?.uppercase() ?: "#"
}
