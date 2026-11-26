package com.smartdialer.app.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartdialer.app.telecom.CallbackReminderReceiver
import org.json.JSONArray
import org.json.JSONObject

data class CallNote(
    val number: String,
    val normalizedKey: String,
    val note: String,
    val tag: String = "General",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class CallbackReminder(
    val id: Int,
    val number: String,
    val displayName: String,
    val note: String = "",
    val triggerAtMillis: Long
)

/**
 * Persists per-number Call Notes and manages scheduled Callback Reminders via AlarmManager.
 */
object CallNotesRepository {

    private const val PREFS_NAME = "smart_dialer_call_notes"
    private const val KEY_NOTES = "call_notes_json"
    private const val KEY_REMINDERS = "callback_reminders_json"

    fun normalizeNumberKey(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits.ifBlank { raw.trim() }
    }

    fun getAllNotes(context: Context): List<CallNote> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        val list = mutableListOf<CallNote>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val noteText = obj.optString("note", "")
                if (noteText.isBlank()) continue
                list.add(
                    CallNote(
                        number = obj.optString("number", ""),
                        normalizedKey = obj.optString("key", ""),
                        note = noteText,
                        tag = obj.optString("tag", "General"),
                        updatedAtMillis = obj.optLong("updatedAt", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.updatedAtMillis }
    }

    fun getNoteForNumber(context: Context, rawNumber: String): CallNote? {
        val key = normalizeNumberKey(rawNumber)
        if (key.isBlank()) return null
        return getAllNotes(context).firstOrNull { it.normalizedKey == key }
    }

    fun saveNote(context: Context, rawNumber: String, noteText: String, tag: String = "General") {
        val key = normalizeNumberKey(rawNumber)
        if (key.isBlank()) return
        val existing = getAllNotes(context).filterNot { it.normalizedKey == key }.toMutableList()
        if (noteText.isNotBlank()) {
            existing.add(
                0,
                CallNote(
                    number = rawNumber.trim(),
                    normalizedKey = key,
                    note = noteText.trim(),
                    tag = tag,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
        persistNotes(context, existing)
    }

    private fun persistNotes(context: Context, notes: List<CallNote>) {
        val arr = JSONArray()
        for (item in notes.take(500)) {
            arr.put(
                JSONObject().apply {
                    put("number", item.number)
                    put("key", item.normalizedKey)
                    put("note", item.note)
                    put("tag", item.tag)
                    put("updatedAt", item.updatedAtMillis)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTES, arr.toString())
            .apply()
    }

    fun getPendingReminders(context: Context): List<CallbackReminder> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        val now = System.currentTimeMillis()
        val list = mutableListOf<CallbackReminder>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val trigger = obj.optLong("triggerAt", 0L)
                if (trigger > now - 60_000L) {
                    list.add(
                        CallbackReminder(
                            id = obj.optInt("id", 0),
                            number = obj.optString("number", ""),
                            displayName = obj.optString("name", ""),
                            note = obj.optString("note", ""),
                            triggerAtMillis = trigger
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedBy { it.triggerAtMillis }
    }

    fun scheduleReminder(
        context: Context,
        number: String,
        displayName: String,
        delayMinutes: Int,
        note: String = ""
    ): CallbackReminder {
        val triggerAt = System.currentTimeMillis() + (delayMinutes * 60_000L)
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt().coerceAtLeast(3001)
        val reminder = CallbackReminder(
            id = id,
            number = number.trim(),
            displayName = displayName.ifBlank { number.trim() },
            note = note.trim(),
            triggerAtMillis = triggerAt
        )

        val updated = getPendingReminders(context).toMutableList().apply { add(reminder) }
        persistReminders(context, updated)

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, CallbackReminderReceiver::class.java).apply {
                action = CallbackReminderReceiver.ACTION_TRIGGER_REMINDER
                putExtra(CallbackReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(CallbackReminderReceiver.EXTRA_NUMBER, reminder.number)
                putExtra(CallbackReminderReceiver.EXTRA_NAME, reminder.displayName)
                putExtra(CallbackReminderReceiver.EXTRA_NOTE, reminder.note)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                reminder.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return reminder
    }

    fun removeReminder(context: Context, reminderId: Int) {
        val updated = getPendingReminders(context).filterNot { it.id == reminderId }
        persistReminders(context, updated)
    }

    private fun persistReminders(context: Context, list: List<CallbackReminder>) {
        val arr = JSONArray()
        for (item in list.take(100)) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("number", item.number)
                    put("name", item.displayName)
                    put("note", item.note)
                    put("triggerAt", item.triggerAtMillis)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REMINDERS, arr.toString())
            .apply()
    }
}
