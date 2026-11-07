package com.smartdialer.app.data.repository

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.ContactAccountInfo
import com.smartdialer.app.data.model.ContactCounts
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.model.ContactSaveDestination
import com.smartdialer.app.data.model.ContactSaveDestinationType
import com.smartdialer.app.data.model.ContactSourceFilter
import com.smartdialer.app.data.model.DuplicateContactGroup
import com.smartdialer.app.utils.WhatsAppHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(val context: Context) {

    suspend fun getContacts(
        searchQuery: String = "",
        filter: ContactSourceFilter = ContactSourceFilter.ALL,
        selectedGoogleAccount: String? = null
    ): List<ContactItem> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext emptyList()
        }

        // Step 1: Map Contact ID -> Account Infos from ContactsContract.RawContacts
        val accountMap = mutableMapOf<String, MutableList<ContactAccountInfo>>()
        val googleContactIds = mutableSetOf<String>()
        val simContactIds = mutableSetOf<String>()
        val deviceContactIds = mutableSetOf<String>()

        try {
            val rawProjection = arrayOf(
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )
            val rawCursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                rawProjection,
                "${ContactsContract.RawContacts.DELETED} = 0",
                null,
                null
            )
            rawCursor?.use { c ->
                val cIdIdx = c.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                val aNameIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val aTypeIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (c.moveToNext()) {
                    val cId = if (cIdIdx != -1) c.getString(cIdIdx) else null
                    val aName = if (aNameIdx != -1) c.getString(aNameIdx) else null
                    val aType = if (aTypeIdx != -1) c.getString(aTypeIdx) else null

                    if (!cId.isNullOrBlank()) {
                        val acc = ContactAccountInfo(aName, aType)
                        accountMap.getOrPut(cId) { mutableListOf() }.add(acc)

                        if (acc.isGoogle) {
                            googleContactIds.add(cId)
                        } else if (aType?.contains("sim", ignoreCase = true) == true ||
                            aType?.contains("icc", ignoreCase = true) == true ||
                            aName?.contains("sim", ignoreCase = true) == true
                        ) {
                            simContactIds.add(cId)
                        } else {
                            deviceContactIds.add(cId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Step 2: Query all phone numbers grouped by CONTACT_ID
        val phonesMap = mutableMapOf<String, MutableList<String>>()
        val phoneContactNames = mutableMapOf<String, String>()
        val phonePhotos = mutableMapOf<String, String?>()
        val phoneLookups = mutableMapOf<String, String?>()

        try {
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
            )

            val phoneCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                phoneProjection,
                null,
                null,
                null
            )

            phoneCursor?.use { c ->
                val cIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val thumbIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                val lookupIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (c.moveToNext()) {
                    val cId = if (cIdIdx != -1) c.getString(cIdIdx) else null
                    val num = if (numIdx != -1) c.getString(numIdx) else null
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null
                    val photo = if (photoIdx != -1) c.getString(photoIdx) else null
                    val thumb = if (thumbIdx != -1) c.getString(thumbIdx) else null
                    val lookup = if (lookupIdx != -1) c.getString(lookupIdx) else null
                    val resolvedPhoto = photo ?: thumb

                    if (!cId.isNullOrBlank() && !num.isNullOrBlank()) {
                        val list = phonesMap.getOrPut(cId) { mutableListOf() }
                        val cleanNum = num.trim()
                        if (!list.contains(cleanNum)) {
                            list.add(cleanNum)
                        }
                        if (!name.isNullOrBlank() && !phoneContactNames.containsKey(cId)) {
                            phoneContactNames[cId] = name
                        }
                        if (resolvedPhoto != null) phonePhotos[cId] = resolvedPhoto
                        if (lookup != null) phoneLookups[cId] = lookup
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Step 3: Query all emails grouped by CONTACT_ID
        val emailsMap = mutableMapOf<String, MutableList<String>>()
        try {
            val emailProjection = arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY
            )

            val emailCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                emailProjection,
                null,
                null,
                null
            )

            emailCursor?.use { c ->
                val cIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val addrIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY)

                while (c.moveToNext()) {
                    val cId = if (cIdIdx != -1) c.getString(cIdIdx) else null
                    val addr = if (addrIdx != -1) c.getString(addrIdx) else null
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null

                    if (!cId.isNullOrBlank() && !addr.isNullOrBlank()) {
                        val list = emailsMap.getOrPut(cId) { mutableListOf() }
                        val cleanAddr = addr.trim()
                        if (!list.contains(cleanAddr)) {
                            list.add(cleanAddr)
                        }
                        if (!name.isNullOrBlank() && !phoneContactNames.containsKey(cId)) {
                            phoneContactNames[cId] = name
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Step 3.5: Query WhatsApp registered profiles from ContactsContract.Data
        val whatsappContactIds = mutableSetOf<String>()
        try {
            val waProjection = arrayOf(
                ContactsContract.Data.CONTACT_ID
            )
            val waCursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                waProjection,
                "${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    "vnd.android.cursor.item/vnd.com.whatsapp.profile",
                    "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
                ),
                null
            )
            waCursor?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getString(idIdx) else null
                    if (!id.isNullOrBlank()) {
                        whatsappContactIds.add(id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Step 4: Query all Contacts from ContactsContract.Contacts
        val allContacts = mutableMapOf<String, ContactItem>()

        try {
            val contactsProjection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.LOOKUP_KEY
            )

            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                contactsProjection,
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val thumbIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                val lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)

                while (c.moveToNext()) {
                    val cId = if (idIdx != -1) c.getString(idIdx) else ""
                    if (cId.isBlank()) continue

                    val name = if (nameIdx != -1) c.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val photo = if (photoIdx != -1) c.getString(photoIdx) else null
                    val thumb = if (thumbIdx != -1) c.getString(thumbIdx) else null
                    val resolvedPhoto = photo ?: thumb ?: phonePhotos[cId]
                    val lookup = if (lookupIdx != -1) c.getString(lookupIdx) else phoneLookups[cId]

                    val phones = phonesMap[cId] ?: emptyList()
                    val emails = emailsMap[cId] ?: emptyList()
                    val accounts = accountMap[cId] ?: emptyList()

                    val isGoogle = googleContactIds.contains(cId) || accounts.any { it.isGoogle }
                    val primeAccount = accounts.firstOrNull { it.isGoogle } ?: accounts.firstOrNull()

                    allContacts[cId] = ContactItem(
                        id = cId,
                        name = name,
                        phoneNumber = phones.firstOrNull() ?: "",
                        phoneNumbers = phones,
                        email = emails.firstOrNull(),
                        emails = emails,
                        photoUri = resolvedPhoto,
                        lookupKey = lookup,
                        accountType = primeAccount?.type,
                        accountName = primeAccount?.name,
                        isGoogleContact = isGoogle,
                        hasWhatsApp = whatsappContactIds.contains(cId)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Merge any contacts that exist in phonesMap, emailsMap, or googleContactIds but weren't in Contacts.CONTENT_URI
        val extraIds = (phonesMap.keys + emailsMap.keys + googleContactIds) - allContacts.keys
        for (extraId in extraIds) {
            val name = phoneContactNames[extraId] ?: "Unknown"
            val phones = phonesMap[extraId] ?: emptyList()
            val emails = emailsMap[extraId] ?: emptyList()
            val accounts = accountMap[extraId] ?: emptyList()
            val isGoogle = googleContactIds.contains(extraId) || accounts.any { it.isGoogle }
            val primeAccount = accounts.firstOrNull { it.isGoogle } ?: accounts.firstOrNull()

            allContacts[extraId] = ContactItem(
                id = extraId,
                name = name,
                phoneNumber = phones.firstOrNull() ?: "",
                phoneNumbers = phones,
                email = emails.firstOrNull(),
                emails = emails,
                photoUri = phonePhotos[extraId],
                lookupKey = phoneLookups[extraId],
                accountType = primeAccount?.type,
                accountName = primeAccount?.name,
                isGoogleContact = isGoogle,
                hasWhatsApp = whatsappContactIds.contains(extraId)
            )
        }

        // Apply source filter
        var resultList = allContacts.values.toList()
        resultList = when (filter) {
            ContactSourceFilter.ALL -> resultList
            ContactSourceFilter.GOOGLE -> {
                if (!selectedGoogleAccount.isNullOrBlank()) {
                    resultList.filter {
                        it.isGoogleContact && (
                            it.accountName.equals(selectedGoogleAccount, ignoreCase = true) ||
                            accountMap[it.id]?.any { acc -> acc.name.equals(selectedGoogleAccount, ignoreCase = true) } == true
                        )
                    }
                } else {
                    resultList.filter { it.isGoogleContact }
                }
            }
            ContactSourceFilter.DEVICE -> resultList.filter { !it.isGoogleContact && !simContactIds.contains(it.id) }
            ContactSourceFilter.SIM -> resultList.filter { simContactIds.contains(it.id) }
        }

        // Apply search query filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            resultList = resultList.filter { contact ->
                contact.name.lowercase().contains(q) ||
                    contact.phoneNumbers.any { it.contains(q) } ||
                    contact.phoneNumber.contains(q) ||
                    contact.emails.any { it.lowercase().contains(q) } ||
                    contact.accountName?.lowercase()?.contains(q) == true
            }
        }

        resultList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun getContactCounts(): ContactCounts = withContext(Dispatchers.IO) {
        val all = getContacts(filter = ContactSourceFilter.ALL)
        val google = all.count { it.isGoogleContact }
        val sim = all.count { it.accountType?.contains("sim", true) == true || it.accountType?.contains("icc", true) == true }
        val device = (all.size - google - sim).coerceAtLeast(0)

        val gAccounts = getGoogleAccounts()
        val gMap = mutableMapOf<String, Int>()
        for (gAcc in gAccounts) {
            gMap[gAcc] = all.count { c ->
                c.isGoogleContact && (
                    c.accountName.equals(gAcc, ignoreCase = true) ||
                    c.email?.equals(gAcc, ignoreCase = true) == true ||
                    c.emails.any { it.equals(gAcc, ignoreCase = true) }
                )
            }
        }

        ContactCounts(
            total = all.size,
            google = google,
            device = device,
            sim = sim,
            googleAccountsMap = gMap
        )
    }

    suspend fun getGoogleAccounts(): List<String> = withContext(Dispatchers.IO) {
        val list = mutableSetOf<String>()
        // From RawContacts
        try {
            val rawCursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.DELETED} = 0",
                null,
                null
            )
            rawCursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val typeIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                while (c.moveToNext()) {
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null
                    val type = if (typeIdx != -1) c.getString(typeIdx) else null
                    if (!name.isNullOrBlank()) {
                        if (type == "com.google" || name.contains("@gmail.com", ignoreCase = true)) {
                            list.add(name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // From AccountManager
        try {
            val am = AccountManager.get(context)
            for (acc in am.getAccountsByType("com.google")) {
                if (!acc.name.isNullOrBlank()) {
                    list.add(acc.name)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        list.toList()
    }

    suspend fun getAvailableSaveDestinations(): List<ContactSaveDestination> = withContext(Dispatchers.IO) {
        val destinations = mutableListOf<ContactSaveDestination>()

        // 1. Google Accounts
        val googleAccounts = getGoogleAccounts()
        if (googleAccounts.isNotEmpty()) {
            for (gAcc in googleAccounts) {
                destinations.add(
                    ContactSaveDestination(
                        type = ContactSaveDestinationType.GOOGLE,
                        title = "Google Account",
                        subtitle = gAcc,
                        accountName = gAcc,
                        accountType = "com.google"
                    )
                )
            }
        } else {
            destinations.add(
                ContactSaveDestination(
                    type = ContactSaveDestinationType.GOOGLE,
                    title = "Google Account",
                    subtitle = "Sync to Google Cloud",
                    accountName = null,
                    accountType = "com.google"
                )
            )
        }

        // 2. Phone Storage
        destinations.add(
            ContactSaveDestination(
                type = ContactSaveDestinationType.PHONE,
                title = "Phone Storage",
                subtitle = "Saved to device memory",
                accountName = null,
                accountType = null
            )
        )

        // 3. SIM Card
        destinations.add(
            ContactSaveDestination(
                type = ContactSaveDestinationType.SIM,
                title = "SIM Card",
                subtitle = "Saved to SIM card memory",
                accountName = "SIM",
                accountType = "com.android.icc"
            )
        )

        destinations
    }

    suspend fun saveNewContact(
        name: String,
        phoneNumber: String,
        secondaryNumber: String? = null,
        email: String? = null,
        company: String? = null,
        destination: ContactSaveDestination
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.failure(SecurityException("WRITE_CONTACTS permission not granted"))
        }

        try {
            val trimmedName = name.trim()
            val trimmedPhone = phoneNumber.trim()

            when (destination.type) {
                ContactSaveDestinationType.GOOGLE -> {
                    val targetAccount = destination.accountName ?: getGoogleAccounts().firstOrNull()
                    val ops = ArrayList<ContentProviderOperation>()
                    val rawIndex = ops.size

                    val rawOp = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.google")
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, targetAccount)
                        .build()
                    ops.add(rawOp)

                    // Display Name
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, trimmedName)
                            .build()
                    )

                    // Primary Phone
                    if (trimmedPhone.isNotBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, trimmedPhone)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                .build()
                        )
                    }

                    // Secondary Phone
                    if (!secondaryNumber.isNullOrBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, secondaryNumber.trim())
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                                .build()
                        )
                    }

                    // Email
                    if (!email.isNullOrBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.trim())
                                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                                .build()
                        )
                    }

                    // Company
                    if (!company.isNullOrBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company.trim())
                                .build()
                        )
                    }

                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    syncGoogleContacts()
                }

                ContactSaveDestinationType.PHONE -> {
                    val ops = ArrayList<ContentProviderOperation>()
                    val rawIndex = ops.size

                    val rawOp = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                    ops.add(rawOp)

                    // Display Name
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, trimmedName)
                            .build()
                    )

                    // Primary Phone
                    if (trimmedPhone.isNotBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, trimmedPhone)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                .build()
                        )
                    }

                    // Secondary Phone
                    if (!secondaryNumber.isNullOrBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, secondaryNumber.trim())
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                                .build()
                        )
                    }

                    // Email
                    if (!email.isNullOrBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.trim())
                                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                                .build()
                        )
                    }

                    // Company
                    if (!company.isNullOrBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company.trim())
                                .build()
                        )
                    }

                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                }

                ContactSaveDestinationType.SIM -> {
                    // Try writing to SIM ICC provider
                    try {
                        val simUri = Uri.parse("content://icc/adn")
                        val values = ContentValues().apply {
                            put("tag", trimmedName.take(20))
                            put("number", trimmedPhone)
                        }
                        context.contentResolver.insert(simUri, values)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Also write to RawContacts with SIM account so it is indexed by system
                    val ops = ArrayList<ContentProviderOperation>()
                    val rawIndex = ops.size

                    val rawOp = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.android.icc")
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "SIM")
                        .build()
                    ops.add(rawOp)

                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, trimmedName)
                            .build()
                    )

                    if (trimmedPhone.isNotBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, trimmedPhone)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                .build()
                        )
                    }

                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun syncGoogleContacts() {
        try {
            val am = AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            for (account in accounts) {
                val bundle = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(account, ContactsContract.AUTHORITY, bundle)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openSyncSettings() {
        try {
            val intent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun openGoogleContactsTrash() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://contacts.google.com/trash")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getContactByNumber(number: String): ContactItem? = withContext(Dispatchers.IO) {
        val clean = number.replace("[^0-9+]".toRegex(), "")
        if (clean.isEmpty()) return@withContext null

        val contacts = getContacts(filter = ContactSourceFilter.ALL)
        contacts.firstOrNull { contact ->
            val primaryClean = contact.phoneNumber.replace("[^0-9+]".toRegex(), "")
            if (primaryClean == clean || (clean.length >= 7 && primaryClean.endsWith(clean.takeLast(7)))) {
                return@firstOrNull true
            }
            contact.phoneNumbers.any { p ->
                val pClean = p.replace("[^0-9+]".toRegex(), "")
                pClean == clean || (clean.length >= 7 && pClean.endsWith(clean.takeLast(7)))
            }
        }
    }

    /**
     * Identifies duplicate contacts by matching normalized phone numbers or identical names.
     */
    suspend fun findDuplicates(): List<DuplicateContactGroup> = withContext(Dispatchers.IO) {
        val all = getContacts(filter = ContactSourceFilter.ALL)
        if (all.size < 2) return@withContext emptyList()

        val groups = mutableMapOf<String, MutableList<ContactItem>>()

        for (c in all) {
            val norm = WhatsAppHelper.normalizeForWhatsApp(c.phoneNumber)
            if (norm.length >= 7) {
                // Match last 10 digits to link local and international versions (017... vs 88017...)
                val shortKey = norm.takeLast(10)
                groups.getOrPut("phone_$shortKey") { mutableListOf() }.add(c)
            } else if (c.name.isNotBlank()) {
                val nameKey = c.name.trim().lowercase()
                groups.getOrPut("name_$nameKey") { mutableListOf() }.add(c)
            }
        }

        val result = mutableListOf<DuplicateContactGroup>()
        val seenContactIds = mutableSetOf<String>()

        for ((key, list) in groups) {
            val distinctContacts = list.distinctBy { it.id }.filter { it.id !in seenContactIds }
            if (distinctContacts.size > 1) {
                // Determine primary contact: prefer Google account contact, then one with email/phone numbers
                val primary = distinctContacts.maxByOrNull { c ->
                    (if (c.isGoogleContact) 10 else 0) +
                    (if (!c.email.isNullOrBlank()) 2 else 0) +
                    c.phoneNumbers.size
                } ?: distinctContacts.first()

                val duplicates = distinctContacts.filter { it.id != primary.id }
                if (duplicates.isNotEmpty()) {
                    result.add(DuplicateContactGroup(key = key, primaryContact = primary, duplicateContacts = duplicates))
                    distinctContacts.forEach { seenContactIds.add(it.id) }
                }
            }
        }

        result
    }

    /**
     * Merges a single duplicate group by removing redundant secondary contacts.
     */
    suspend fun mergeDuplicateGroup(group: DuplicateContactGroup): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var mergedCount = 0
            for (dup in group.duplicateContacts) {
                val contactUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI,
                    dup.id.toLongOrNull() ?: continue
                )
                val rows = context.contentResolver.delete(contactUri, null, null)
                if (rows > 0) {
                    mergedCount++
                }
            }
            Result.success(mergedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Batch merges all detected duplicate contact groups.
     */
    suspend fun mergeAllDuplicates(groups: List<DuplicateContactGroup>): Result<Int> = withContext(Dispatchers.IO) {
        var totalMerged = 0
        for (group in groups) {
            val res = mergeDuplicateGroup(group)
            if (res.isSuccess) {
                totalMerged += res.getOrDefault(0)
            }
        }
        Result.success(totalMerged)
    }
}
