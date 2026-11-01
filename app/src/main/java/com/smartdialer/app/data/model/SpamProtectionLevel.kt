package com.smartdialer.app.data.model

/**
 * 3-Layer Spam Protection Model:
 * 1. BASIC (Layer 1): Prominent visual warning banner on incoming call & history. Normal ringing.
 * 2. SMART (Layer 2 - Recommended): Auto-silences confirmed spam and telemarketers. Flags suspicious spoofed prefixes.
 * 3. MAXIMUM (Layer 3): Strict auto-rejection of high-risk spam. Optional block for private/unknown numbers.
 */
enum class SpamProtectionLevel(
    val layerNumber: Int,
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val actionSummary: String
) {
    BASIC(
        layerNumber = 1,
        id = "basic",
        title = "Basic (মৌলিক)",
        subtitle = "স্ক্রিনে লাল সতর্কবার্তা (স্বাভাবিক রিং)",
        description = "পরিচিত স্প্যাম ও প্রতারক চক্র শনাক্ত করে স্ক্রিনে লাল সতর্কবার্তা প্রদর্শন করে।",
        actionSummary = "Warn only (Screen alert, normal ringing)"
    ),
    SMART(
        layerNumber = 2,
        id = "smart",
        title = "Smart (স্মার্ট সুরক্ষা)",
        subtitle = "স্প্যাম রিংটোন অটো-সাইলেন্ট ও ভুয়া নম্বর ফিল্টারিং",
        description = "স্প্যাম কল আসলে রিংটোন অটো-সাইলেন্ট করে এবং ভুয়া নম্বর চিহ্নিত করে।",
        actionSummary = "Auto-silence spam ringer & screen spoofed calls"
    ),
    MAXIMUM(
        layerNumber = 3,
        id = "maximum",
        title = "Maximum (সর্বোচ্চ কড়া)",
        subtitle = "স্প্যাম কল সাথে সাথে অটো-রিজেক্ট ও ব্লকিং",
        description = "উচ্চ ঝুঁকিপূর্ণ স্প্যাম ও প্রতারক নম্বর সাথে সাথে অটো-রিজেক্ট করে।",
        actionSummary = "Auto-block & reject high-risk spam instantly"
    );

    companion object {
        fun fromId(id: String?): SpamProtectionLevel =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SMART
    }
}

enum class SpamRiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED
}

enum class SpamAction {
    NONE,
    WARN,
    SILENCE,
    REJECT
}

data class SpamCheckResult(
    val isSpam: Boolean = false,
    val isBlocked: Boolean = false,
    val riskLevel: SpamRiskLevel = SpamRiskLevel.NONE,
    val category: String = "",
    val reason: String = "",
    val action: SpamAction = SpamAction.NONE
)
