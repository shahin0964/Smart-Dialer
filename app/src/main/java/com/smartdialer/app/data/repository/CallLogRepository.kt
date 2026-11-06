package com.smartdialer.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.CallLogItem
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.model.GroupedCallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CallLogRepository(private val context: Context) {

    suspend fun getCallLogs(filterType: CallType? = null): List<CallLogItem> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext emptyList()
        }

        val logs = mutableListOf<CallLogItem>()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_PHOTO_URI
        )

        val sortOrder = "${CallLog.Calls.DATE} DESC"

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        val now = Calendar.getInstance()
        val todayYear = now.get(Calendar.YEAR)
        val todayDay = now.get(Calendar.DAY_OF_YEAR)

        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayYear = now.get(Calendar.YEAR)
        val yesterdayDay = now.get(Calendar.DAY_OF_YEAR)

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val photoIdx = it.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx != -1) it.getString(nameIdx) else null
                    val number = if (numberIdx != -1) it.getString(numberIdx) ?: "" else ""
                    val typeInt = if (typeIdx != -1) it.getInt(typeIdx) else 0
                    val dateMillis = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                    val duration = if (durIdx != -1) it.getLong(durIdx) else 0L
                    val photoUri = if (photoIdx != -1) it.getString(photoIdx) else null

                    val callType = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        else -> CallType.UNKNOWN
                    }

                    if (filterType != null && callType != filterType) {
                        continue
                    }

                    val spamResult = com.smartdialer.app.data.repository.SpamProtectionManager.checkNumber(
                        context = context,
                        rawNumber = number,
                        contactName = name
                    )

                    val callCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    val isToday = (callCal.get(Calendar.YEAR) == todayYear && callCal.get(Calendar.DAY_OF_YEAR) == todayDay)
                    val isYesterday = (callCal.get(Calendar.YEAR) == yesterdayYear && callCal.get(Calendar.DAY_OF_YEAR) == yesterdayDay)

                    val formattedTime = timeFormat.format(Date(dateMillis))
                    val formattedDate = dateFormat.format(Date(dateMillis))

                    logs.add(
                        CallLogItem(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            type = callType,
                            dateMillis = dateMillis,
                            durationSeconds = duration,
                            formattedTime = formattedTime,
                            formattedDate = formattedDate,
                            isToday = isToday,
                            isYesterday = isYesterday,
                            photoUri = photoUri,
                            isSpam = spamResult.isSpam,
                            spamCategory = if (spamResult.isSpam) spamResult.category else null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        logs
    }

    suspend fun deleteCallLog(id: String): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext false
        }
        try {
            val uri = CallLog.Calls.CONTENT_URI
            val rows = context.contentResolver.delete(uri, "${CallLog.Calls._ID} = ?", arrayOf(id))
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteCallLogs(ids: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED || ids.isEmpty()
        ) {
            return@withContext false
        }
        try {
            val uri = CallLog.Calls.CONTENT_URI
            val placeholders = ids.joinToString(",") { "?" }
            val rows = context.contentResolver.delete(uri, "${CallLog.Calls._ID} IN ($placeholders)", ids.toTypedArray())
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteCallLogsForNumber(number: String): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext false
        }
        try {
            val clean = number.trim()
            val uri = CallLog.Calls.CONTENT_URI
            val rows = context.contentResolver.delete(
                uri,
                "${CallLog.Calls.NUMBER} = ? OR ${CallLog.Calls.NUMBER} = ? OR ${CallLog.Calls.NUMBER} = ?",
                arrayOf(clean, "+88$clean", if (clean.startsWith("0")) clean.removePrefix("0") else clean)
            )
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        fun normalizeNumberForGrouping(raw: String): String {
            val clean = raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            if (clean.isBlank()) return "unknown"
            if (clean.startsWith("+8801") && clean.length == 14) {
                return "0" + clean.removePrefix("+88")
            }
            if (clean.startsWith("8801") && clean.length == 13) {
                return "0" + clean.removePrefix("88")
            }
            if (clean.startsWith("008801") && clean.length == 16) {
                return "0" + clean.removePrefix("0088")
            }
            return clean
        }

        fun groupCallLogs(rawLogs: List<CallLogItem>): List<GroupedCallLog> {
            val groupsMap = linkedMapOf<String, MutableList<CallLogItem>>()

            for (log in rawLogs) {
                val norm = normalizeNumberForGrouping(log.phoneNumber)
                val key = if (norm == "unknown") "unknown_${log.id}" else norm
                val list = groupsMap.getOrPut(key) { mutableListOf() }
                list.add(log)
            }

            return groupsMap.map { (key, callList) ->
                val sortedCalls = callList.sortedByDescending { it.dateMillis }
                val latest = sortedCalls.first()
                val bestName = sortedCalls.firstOrNull { !it.name.isNullOrBlank() }?.name ?: latest.name
                val bestPhoto = sortedCalls.firstOrNull { !it.photoUri.isNullOrBlank() }?.photoUri ?: latest.photoUri
                val isAnySpam = sortedCalls.any { it.isSpam }
                val spamCat = sortedCalls.firstOrNull { it.isSpam }?.spamCategory

                GroupedCallLog(
                    groupKey = key,
                    primaryNumber = latest.phoneNumber,
                    contactName = bestName,
                    photoUri = bestPhoto,
                    isSpam = isAnySpam,
                    spamCategory = spamCat,
                    calls = sortedCalls
                )
            }
        }
    }
}
