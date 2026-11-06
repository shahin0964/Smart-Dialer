package com.smartdialer.app.data.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.FileProvider
import com.smartdialer.app.data.model.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object VcfManager {

    suspend fun exportContactsToVcf(context: Context, contacts: List<ContactItem>): Result<File> = withContext(Dispatchers.IO) {
        try {
            val vcfFile = File(context.cacheDir, "smart_dialer_contacts_backup.vcf")
            vcfFile.bufferedWriter().use { writer ->
                for (contact in contacts) {
                    writer.write("BEGIN:VCARD\r\n")
                    writer.write("VERSION:3.0\r\n")
                    writer.write("FN:${contact.name.replace("\n", "").trim()}\r\n")
                    for (phone in contact.phoneNumbers) {
                        val cleanPhone = phone.trim()
                        if (cleanPhone.isNotBlank()) {
                            writer.write("TEL;TYPE=CELL:$cleanPhone\r\n")
                        }
                    }
                    if (contact.phoneNumbers.isEmpty() && contact.phoneNumber.isNotBlank()) {
                        writer.write("TEL;TYPE=CELL:${contact.phoneNumber.trim()}\r\n")
                    }
                    for (email in contact.emails) {
                        val cleanEmail = email.trim()
                        if (cleanEmail.isNotBlank()) {
                            writer.write("EMAIL;TYPE=INTERNET:$cleanEmail\r\n")
                        }
                    }
                    writer.write("END:VCARD\r\n")
                }
            }
            Result.success(vcfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun shareVcfFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Export Contacts (.vcf)").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun importContactsFromVcf(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(Exception("Cannot open file stream"))
            var importedCount = 0

            var currentName: String? = null
            val currentPhones = mutableListOf<String>()
            val currentEmails = mutableListOf<String>()

            BufferedReader(InputStreamReader(stream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
                        currentName = null
                        currentPhones.clear()
                        currentEmails.clear()
                    } else if (trimmed.startsWith("FN:", ignoreCase = true)) {
                        currentName = trimmed.substring(3).trim()
                    } else if (trimmed.startsWith("TEL", ignoreCase = true)) {
                        val parts = trimmed.split(":")
                        if (parts.size >= 2) {
                            val num = parts.subList(1, parts.size).joinToString(":").trim()
                            if (num.isNotBlank()) currentPhones.add(num)
                        }
                    } else if (trimmed.startsWith("EMAIL", ignoreCase = true)) {
                        val parts = trimmed.split(":")
                        if (parts.size >= 2) {
                            val em = parts.subList(1, parts.size).joinToString(":").trim()
                            if (em.isNotBlank()) currentEmails.add(em)
                        }
                    } else if (trimmed.equals("END:VCARD", ignoreCase = true)) {
                        val nameToSave = currentName ?: currentPhones.firstOrNull() ?: "Unknown"
                        if (currentPhones.isNotEmpty()) {
                            insertContact(context, nameToSave, currentPhones, currentEmails)
                            importedCount++
                        }
                    }
                    line = reader.readLine()
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun insertContact(
        context: Context,
        name: String,
        phoneNumbers: List<String>,
        emails: List<String>
    ) {
        try {
            val ops = ArrayList<ContentProviderOperation>()
            val rawContactInsertIndex = ops.size

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Name
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )

            // Phone numbers
            for (phone in phoneNumbers) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }

            // Emails
            for (email in emails) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
