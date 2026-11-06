package com.smartdialer.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.CallType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IpCallRecord(
    val id: String,
    val number: String,
    val contactName: String?,
    val callType: CallType,
    val durationSeconds: Long,
    val timestampMillis: Long
) {
    val displayName: String
        get() = if (!contactName.isNullOrBlank()) contactName else number

    val formattedDuration: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return when {
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }

    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestampMillis))
        }
}

data class ContactTalkStat(
    val number: String,
    val name: String?,
    val totalDurationSeconds: Long,
    val callCount: Int
) {
    val displayName: String
        get() = if (!name.isNullOrBlank()) name else number

    val formattedDuration: String
        get() {
            val minutes = totalDurationSeconds / 60
            val seconds = totalDurationSeconds % 60
            return when {
                minutes > 0 -> "${minutes}m ${seconds}s"
                else -> "${seconds}s"
            }
        }
}

object IpCallStatsRepository {

    private const val PREFS_NAME = "smart_dialer_ip_call_stats"
    private const val KEY_HISTORY = "ip_call_history_json"

    fun recordCall(
        context: Context,
        number: String,
        name: String?,
        type: CallType,
        durationSeconds: Long
    ) {
        if (number.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getCallHistory(context).toMutableList()

        val newRecord = IpCallRecord(
            id = "ip_${System.currentTimeMillis()}_${(100..999).random()}",
            number = number,
            contactName = name,
            callType = type,
            durationSeconds = durationSeconds,
            timestampMillis = System.currentTimeMillis()
        )

        // Add to front of history
        existing.add(0, newRecord)

        // Keep maximum 300 entries
        val trimmed = if (existing.size > 300) existing.take(300) else existing

        // Save JSON
        try {
            val jsonArray = JSONArray()
            for (rec in trimmed) {
                val obj = JSONObject().apply {
                    put("id", rec.id)
                    put("number", rec.number)
                    put("name", rec.contactName ?: "")
                    put("type", rec.callType.name)
                    put("duration", rec.durationSeconds)
                    put("time", rec.timestampMillis)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Also write to Android native CallLog if permission is available
        tryWriteToAndroidCallLog(context, number, type, durationSeconds)
    }

    private fun tryWriteToAndroidCallLog(
        context: Context,
        number: String,
        type: CallType,
        durationSeconds: Long
    ) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val callTypeInt = when (type) {
                    CallType.INCOMING -> CallLog.Calls.INCOMING_TYPE
                    CallType.OUTGOING -> CallLog.Calls.OUTGOING_TYPE
                    CallType.MISSED -> CallLog.Calls.MISSED_TYPE
                    CallType.REJECTED -> CallLog.Calls.REJECTED_TYPE
                    CallType.UNKNOWN -> CallLog.Calls.OUTGOING_TYPE
                }

                val values = ContentValues().apply {
                    put(CallLog.Calls.NUMBER, number)
                    put(CallLog.Calls.DATE, System.currentTimeMillis())
                    put(CallLog.Calls.DURATION, durationSeconds)
                    put(CallLog.Calls.TYPE, callTypeInt)
                    put(CallLog.Calls.NEW, 1)
                }

                context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCallHistory(context: Context): List<IpCallRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        val list = mutableListOf<IpCallRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val number = obj.optString("number", "")
                val name = obj.optString("name", "").ifBlank { null }
                val typeStr = obj.optString("type", CallType.OUTGOING.name)
                val callType = try {
                    CallType.valueOf(typeStr)
                } catch (e: Exception) {
                    CallType.OUTGOING
                }
                val duration = obj.optLong("duration", 0L)
                val time = obj.optLong("time", 0L)

                list.add(
                    IpCallRecord(
                        id = id,
                        number = number,
                        contactName = name,
                        callType = callType,
                        durationSeconds = duration,
                        timestampMillis = time
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getTotalTalkTimeSeconds(context: Context): Long {
        return getCallHistory(context).sumOf { it.durationSeconds }
    }

    fun getTalkTimeByContact(context: Context): List<ContactTalkStat> {
        val history = getCallHistory(context)
        val map = mutableMapOf<String, Pair<String?, MutableList<Long>>>()

        for (rec in history) {
            val key = rec.number.trim()
            if (key.isBlank()) continue
            val existing = map.getOrPut(key) { Pair(rec.contactName, mutableListOf()) }
            existing.second.add(rec.durationSeconds)
        }

        return map.map { (number, pair) ->
            ContactTalkStat(
                number = number,
                name = pair.first,
                totalDurationSeconds = pair.second.sum(),
                callCount = pair.second.size
            )
        }.sortedByDescending { it.totalDurationSeconds }
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
