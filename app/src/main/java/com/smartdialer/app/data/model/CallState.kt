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

data class ActiveCallInfo(
    val callId: String = "",
    val number: String = "",
    val contactName: String = "",
    val status: CallStatus = CallStatus.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isHolding: Boolean = false,
    val isRecording: Boolean = false,
    val simSlot: Int = 1
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
