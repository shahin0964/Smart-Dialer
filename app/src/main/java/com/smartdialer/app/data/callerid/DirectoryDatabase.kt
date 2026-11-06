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
        "1098" to "National Child Helpline (Govt)",
        "16575" to "National Consumer Rights Helpline",
        "16444" to "Disaster Management Helpline",

        // Mobile Financial Services (MFS) & Fintech
        "16247" to "bKash Customer Care",
        "16716" to "Nagad Customer Care",
        "16222" to "Rocket (Dutch-Bangla Bank)",
        "16258" to "Upay (UCB Fintech)",
        "16268" to "SureCash Customer Care",
        "16789" to "Tap (Trust Axiata Pay)",

        // Major Banks in Bangladesh
        "16214" to "Sonali Bank PLC Helpline",
        "16234" to "Islami Bank Bangladesh Helpline",
        "16219" to "City Bank PLC Helpline",
        "16213" to "BRAC Bank PLC Helpline",
        "16206" to "Eastern Bank Limited (EBL) Helpline",
        "16269" to "Standard Chartered Bank Helpline",
        "16419" to "United Commercial Bank (UCB) Helpline",
        "16218" to "Prime Bank Limited Helpline",
        "16238" to "Mutual Trust Bank (MTB) Helpline",
        "16259" to "Bank Asia Limited Helpline",
        "16245" to "Southeast Bank PLC Helpline",
        "16267" to "Pubali Bank PLC Helpline",
        "16256" to "Trust Bank Limited Helpline",
        "16277" to "Jamuna Bank Limited Helpline",
        "16246" to "Dhaka Bank PLC Helpline",
        "16323" to "Al-Arafah Islami Bank Helpline",
        "16310" to "Mercantile Bank PLC Helpline",
        "16230" to "Janata Bank PLC Helpline",
        "16235" to "Agrani Bank PLC Helpline",
        "16236" to "Rupali Bank PLC Helpline",
        "16261" to "One Bank Limited Helpline",
        "16255" to "EXIM Bank Limited Helpline",
        "16257" to "Shahjalal Islami Bank Helpline",

        // Telecom Operators Customer Care
        "121" to "Mobile Operator Customer Care",
        "171" to "Grameenphone Special Care",
        "191" to "Banglalink Special Care",
        "181" to "Robi Special Care",
        "151" to "Teletalk Special Care",
        "16000" to "BTCL Customer Care & Broadband",

        // Major Hospitals & Healthcare
        "10678" to "Evercare Hospital Dhaka",
        "10666" to "Square Hospital Dhaka",
        "10616" to "United Hospital Dhaka",
        "10620" to "Labaid Specialized Hospital",
        "10610" to "Evercare Hospital Chattogram",
        "10633" to "Popular Diagnostic Centre Helpline",
        "10606" to "Ibn Sina Hospital Helpline",

        // Major Courier, Delivery & Rideshare
        "09612345678" to "Foodpanda Bangladesh Care",
        "09678121121" to "Pathao Support Hotline",
        "09610001000" to "Daraz Bangladesh Customer Care",
        "09612221222" to "RedX Delivery Hotline",
        "09612444888" to "Paperfly Courier Support",
        "09610007339" to "eCourier Support Hotline",
        "09612001000" to "Sundarban Courier Service",
        "09612002000" to "SA Paribahan Courier Hotline",

        // Airlines & Travel
        "13636" to "Biman Bangladesh Airlines Helpline",
        "13603" to "US-Bangla Airlines Hotline",
        "13605" to "Air Astra Hotline",
        "13607" to "Novoair Hotline"
    )

    fun lookup(rawNumber: String): String? {
        val clean = rawNumber.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")

        val digits = clean.filter { it.isDigit() }
        if (digits.isBlank()) return null

        // 1. Direct match
        DIRECTORY[digits]?.let { return it }

        // 2. Strip international 880 prefix if present
        if (digits.startsWith("880") && digits.length > 3) {
            val stripped = digits.removePrefix("880")
            DIRECTORY[stripped]?.let { return it }
            DIRECTORY["0$stripped"]?.let { return it }
        }

        // 3. Strip leading 0
        if (digits.startsWith("0") && digits.length > 1) {
            val strippedZero = digits.removePrefix("0")
            DIRECTORY[strippedZero]?.let { return it }
        }

        // 4. Try matching 5-digit short codes at the end
        if (digits.length >= 5) {
            val last5 = digits.takeLast(5)
            DIRECTORY[last5]?.let { return it }
        }

        // 5. Try matching 3 or 4 digit short codes (e.g. 999, 333, 121, 106, 109)
        if (digits.length in 3..4) {
            DIRECTORY[digits]?.let { return it }
        }

        return null
    }
}
