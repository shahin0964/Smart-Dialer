package com.smartdialer.app.data.callerid

object DirectoryDatabase {

    private val DIRECTORY = mapOf(
        // Emergency & Public Services
        "999" to "National Emergency Service (Police, Fire, Ambulance)",
        "333" to "National Information & Citizen Services",
        "109" to "Violence Against Women & Children Helpline",
        "106" to "Anti-Corruption Commission (ACC) Helpline",
        "16122" to "Land Ministry Citizen Helpline",
        "16263" to "Shastho Batayan (National Health Helpline)",
        "16430" to "National Legal Aid Services Helpline",
        "16100" to "Passport & Immigration Helpline",
        "16216" to "Election Commission / NID Helpline",
        "105" to "National Identity Card (NID) Service",
        "100" to "Bangladesh Police Control Room",
        "102" to "Fire Service & Civil Defence Control Room",
        "158" to "BTRC Telecom Complaints Helpline",
        "1090" to "Disaster Early Warning & Weather Helpline",
        "13219" to "Child Helpline (UNICEF / DSS)",

        // Mobile Financial Services (MFS) & Banks
        "16247" to "bKash Customer Care",
        "16716" to "Nagad Customer Care",
        "16222" to "Rocket (Dutch-Bangla Bank)",
        "16214" to "Sonali Bank Helpline",
        "16234" to "Islami Bank Bangladesh Helpline",
        "16219" to "City Bank Helpline",
        "16213" to "BRAC Bank Helpline",
        "16206" to "Eastern Bank Limited (EBL) Helpline",
        "16269" to "Standard Chartered Bank Helpline",

        // Telecom Operators
        "121" to "Mobile Operator Customer Care",
        "171" to "Grameenphone Special Care",
        "191" to "Banglalink Special Care",
        "181" to "Robi Special Care",
        "151" to "Teletalk Special Care"
    )

    fun lookup(rawNumber: String): String? {
        val clean = rawNumber.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")

        val digits = clean.filter { it.isDigit() }
        return DIRECTORY[digits]
    }
}
