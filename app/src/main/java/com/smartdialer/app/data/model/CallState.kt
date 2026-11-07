package com.smartdialer.app.data.model

enum class CallStatus {
    IDLE,
    CONNECTING,
    DIALING,
    RINGING,
    ACTIVE,
    HOLDING,
    DISCONNECTED
}

data class NetworkQualityInfo(
    val rttMs: Int = 0,
    val packetLossPct: Float = 0f,
    val jitterMs: Int = 0,
    val label: String = "HD Voice",
    val qualityLevel: Int = 3 // 3 = HD/Excellent, 2 = Good, 1 = Fair, 0 = Poor
)

data class ActiveCallInfo(
    val callId: String = "",
    val number: String = "",
    val contactName: String = "",
    val photoUri: String? = null,
    val isSpam: Boolean = false,
    val spamCategory: String? = null,
    val isSilencedSpam: Boolean = false,
    val isBlocked: Boolean = false,
    val status: CallStatus = CallStatus.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isHolding: Boolean = false,
    val isRecording: Boolean = false,
    val simSlot: Int = 1,
    val isIpCall: Boolean = false,
    val isIncoming: Boolean = false,
    val networkQuality: NetworkQualityInfo? = null
) {
    val durationFormatted: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            val hours = durationSeconds / 3600
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val displayTitle: String
        get() = if (contactName.isNotBlank()) contactName else number
}
