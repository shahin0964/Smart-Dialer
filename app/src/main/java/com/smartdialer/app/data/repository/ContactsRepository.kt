package com.smartdialer.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    suspend fun getContacts(searchQuery: String = ""): List<ContactItem> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext emptyList()
        }

        val contactsList = mutableListOf<ContactItem>()
        val seenNumbers = mutableSetOf<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        val selection = if (searchQuery.isNotBlank()) {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else {
            null
        }

        val selectionArgs = if (searchQuery.isNotBlank()) {
            arrayOf("%$searchQuery%", "%$searchQuery%")
        } else {
            null
        }

        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val lookupIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) ?: "" else ""
                    val name = if (nameIndex != -1) it.getString(nameIndex) ?: "Unknown" else "Unknown"
                    val rawNumber = if (numberIndex != -1) it.getString(numberIndex) ?: "" else ""
                    val cleanNumber = rawNumber.replace("\\s+".toRegex(), "").replace("-", "")

                    if (cleanNumber.isNotBlank() && !seenNumbers.contains(cleanNumber)) {
                        seenNumbers.add(cleanNumber)
                        val photoUri = if (photoIndex != -1) it.getString(photoIndex) else null
                        val lookup = if (lookupIndex != -1) it.getString(lookupIndex) else null

                        contactsList.add(
                            ContactItem(
                                id = id,
                                name = name,
                                phoneNumber = rawNumber,
                                photoUri = photoUri,
                                lookupKey = lookup
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        contactsList
    }

    suspend fun getContactByNumber(number: String): ContactItem? = withContext(Dispatchers.IO) {
        val clean = number.replace("[^0-9+]".toRegex(), "")
        if (clean.isEmpty()) return@withContext null
        val contacts = getContacts(clean)
        contacts.firstOrNull()
    }
}
