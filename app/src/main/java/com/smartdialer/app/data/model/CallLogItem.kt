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

    val durationFormatted: String
        get() {
            if (durationSeconds <= 0) {
                return when (type) {
                    CallType.MISSED -> "Missed"
                    CallType.REJECTED -> "Declined"
                    else -> "0s"
                }
            }
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
}

data class GroupedCallLog(
    val groupKey: String,
    val primaryNumber: String,
    val contactName: String?,
    val photoUri: String?,
    val isSpam: Boolean,
    val spamCategory: String?,
    val calls: List<CallLogItem>
) {
    val totalCount: Int get() = calls.size
    val latestCall: CallLogItem get() = calls.first()
    val displayName: String get() = if (!contactName.isNullOrBlank()) contactName else primaryNumber
    val initial: String get() = displayName.trim().firstOrNull()?.uppercase() ?: "#"
    val isToday: Boolean get() = latestCall.isToday
    val isYesterday: Boolean get() = latestCall.isYesterday
    val totalDurationSeconds: Long get() = calls.sumOf { it.durationSeconds }

    val totalDurationFormatted: String
        get() {
            val totalSec = totalDurationSeconds
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
}
