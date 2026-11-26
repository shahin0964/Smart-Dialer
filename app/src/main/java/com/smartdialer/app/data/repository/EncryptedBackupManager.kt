package com.smartdialer.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM Password-Encrypted Full App Backup & Restore Manager.
 * Encrypts AppSettingsPreferences, CallNotes, and SIP Account profiles using PBKDF2WithHmacSHA256.
 */
object EncryptedBackupManager {

    private const val MAGIC = "SMART_DIALER_AES256_V1"
    private const val PBKDF2_ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128

    fun exportToBytes(context: Context, password: String): ByteArray {
        require(password.length >= 4) { "Password must be at least 4 characters" }

        val payloadJson = buildBackupPayload(context).toString()
        val plaintext = payloadJson.toByteArray(StandardCharsets.UTF_8)

        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val secretKey = deriveAesKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val envelope = JSONObject().apply {
            put("magic", MAGIC)
            put("createdAt", System.currentTimeMillis())
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("data", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        }
        return envelope.toString(2).toByteArray(StandardCharsets.UTF_8)
    }

    fun restoreFromBytes(context: Context, rawBytes: ByteArray, password: String): String {
        val rawStr = String(rawBytes, StandardCharsets.UTF_8)
        val envelope = JSONObject(rawStr)
        if (envelope.optString("magic") != MAGIC) {
            throw IllegalArgumentException("Invalid Smart Dialer backup file")
        }

        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)

        val secretKey = deriveAesKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LEN_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)

        val payload = JSONObject(String(plaintext, StandardCharsets.UTF_8))
        return applyBackupPayload(context, payload)
    }

    fun writeToUri(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Unable to open output stream")
    }

    fun readFromUri(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read backup file")
    }

    fun createTempShareFile(context: Context, bytes: ByteArray): File {
        val file = File(context.cacheDir, "smart_dialer_backup_${System.currentTimeMillis()}.sdbackup")
        file.writeBytes(bytes)
        return file
    }

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun buildBackupPayload(context: Context): JSONObject {
        val appSettings = AppSettingsPreferences(context)
        val ipPrefs = IpCallPreferences(context)
        val notes = CallNotesRepository.getAllNotes(context)

        val appObj = JSONObject().apply {
            put("preferredSimSlot", appSettings.preferredSimSlot)
            put("askEveryTime", appSettings.askEveryTime)
            put("autoRecord", appSettings.autoRecord)
            put("recordUnknownOnly", appSettings.recordUnknownOnly)
            put("audioFormat", appSettings.audioFormat)
            put("dialpadTones", appSettings.dialpadTones)
            put("dialpadVibration", appSettings.dialpadVibration)
            put("callConnectedVibration", appSettings.callConnectedVibration)
            put("callEndedVibration", appSettings.callEndedVibration)
            put("blockUnknownNumbers", appSettings.blockUnknownNumbers)
            put("blockHiddenNumbers", appSettings.blockHiddenNumbers)
            put("spamProtectionEnabled", appSettings.spamProtectionEnabled)
            put("spamProtectionLevel", appSettings.spamProtectionLevel.id)
            put("silenceUnknownCallers", appSettings.silenceUnknownCallers)
            put("callerTheme", appSettings.callerTheme)
            put("appLockEnabled", appSettings.appLockEnabled)
            put("flipToSilenceEnabled", appSettings.flipToSilenceEnabled)
            put("flashAlertEnabled", appSettings.flashAlertEnabled)
            put("callerAnnouncerEnabled", appSettings.callerAnnouncerEnabled)
            put("postCallSummaryEnabled", appSettings.postCallSummaryEnabled)
            put("callingCountryCode", appSettings.callingCountryCode)
            put("callingCountryName", appSettings.callingCountryName)
            put("voicemailNumber", appSettings.voicemailNumber)
            put("blockedNumbers", JSONArray(appSettings.blockedNumbers.toList()))
            put("reportedSpam", JSONArray(appSettings.reportedSpamNumbers.toList()))
            put("quickReplies", JSONArray(appSettings.getQuickReplyTemplates()))

            val speedArr = JSONArray()
            appSettings.getAllSpeedDials().forEach { (digit, pair) ->
                speedArr.put(JSONObject().apply {
                    put("digit", digit)
                    put("number", pair.first)
                    put("name", pair.second)
                })
            }
            put("speedDials", speedArr)
        }

        val notesArr = JSONArray()
        notes.forEach { n ->
            notesArr.put(JSONObject().apply {
                put("number", n.number)
                put("note", n.note)
                put("tag", n.tag)
            })
        }

        val sipArr = JSONArray()
        ipPrefs.getSavedAccounts().forEach { acc ->
            sipArr.put(acc.toJson())
        }

        return JSONObject().apply {
            put("appSettings", appObj)
            put("notes", notesArr)
            put("sipAccounts", sipArr)
            put("activeSipAccountId", ipPrefs.activeAccountId)
        }
    }

    private fun applyBackupPayload(context: Context, payload: JSONObject): String {
        val appSettings = AppSettingsPreferences(context)
        val ipPrefs = IpCallPreferences(context)

        var notesCount = 0
        var sipCount = 0

        payload.optJSONObject("appSettings")?.let { appObj ->
            appSettings.preferredSimSlot = appObj.optInt("preferredSimSlot", -1)
            appSettings.askEveryTime = appObj.optBoolean("askEveryTime", true)
            appSettings.autoRecord = appObj.optBoolean("autoRecord", false)
            appSettings.recordUnknownOnly = appObj.optBoolean("recordUnknownOnly", false)
            appSettings.audioFormat = appObj.optString("audioFormat", "AAC / M4A")
            appSettings.dialpadTones = appObj.optBoolean("dialpadTones", true)
            appSettings.dialpadVibration = appObj.optBoolean("dialpadVibration", true)
            appSettings.callConnectedVibration = appObj.optBoolean("callConnectedVibration", true)
            appSettings.callEndedVibration = appObj.optBoolean("callEndedVibration", true)
            appSettings.blockUnknownNumbers = appObj.optBoolean("blockUnknownNumbers", false)
            appSettings.blockHiddenNumbers = appObj.optBoolean("blockHiddenNumbers", false)
            appSettings.spamProtectionEnabled = appObj.optBoolean("spamProtectionEnabled", true)
            appSettings.spamProtectionLevel = com.smartdialer.app.data.model.SpamProtectionLevel.fromId(
                appObj.optString("spamProtectionLevel", "smart")
            )
            appSettings.silenceUnknownCallers = appObj.optBoolean("silenceUnknownCallers", false)
            appSettings.callerTheme = appObj.optString("callerTheme", "CLASSIC_DARK")
            appSettings.appLockEnabled = appObj.optBoolean("appLockEnabled", false)
            appSettings.flipToSilenceEnabled = appObj.optBoolean("flipToSilenceEnabled", true)
            appSettings.flashAlertEnabled = appObj.optBoolean("flashAlertEnabled", false)
            appSettings.callerAnnouncerEnabled = appObj.optBoolean("callerAnnouncerEnabled", false)
            appSettings.postCallSummaryEnabled = appObj.optBoolean("postCallSummaryEnabled", true)
            appSettings.callingCountryCode = appObj.optString("callingCountryCode", "+880")
            appSettings.callingCountryName = appObj.optString("callingCountryName", "Bangladesh")
            appSettings.voicemailNumber = appObj.optString("voicemailNumber", "*121#")

            appObj.optJSONArray("blockedNumbers")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { set.add(it) }
                appSettings.blockedNumbers = set
            }
            appObj.optJSONArray("reportedSpam")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { set.add(it) }
                appSettings.reportedSpamNumbers = set
            }
            appObj.optJSONArray("quickReplies")?.let { arr ->
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { list.add(it) }
                if (list.isNotEmpty()) appSettings.setQuickReplyTemplates(list)
            }
            appObj.optJSONArray("speedDials")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val digit = item.optInt("digit", -1)
                    val num = item.optString("number", "")
                    val name = item.optString("name", "")
                    if (digit in 1..9 && num.isNotBlank()) {
                        appSettings.setSpeedDial(digit, num, name)
                    }
                }
            }
        }

        payload.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i) ?: continue
                val num = n.optString("number", "")
                val note = n.optString("note", "")
                val tag = n.optString("tag", "General")
                if (num.isNotBlank() && note.isNotBlank()) {
                    CallNotesRepository.saveNote(context, num, note, tag)
                    notesCount++
                }
            }
        }

        payload.optJSONArray("sipAccounts")?.let { arr ->
            val accounts = mutableListOf<SipAccountProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                accounts.add(SipAccountProfile.fromJson(obj))
                sipCount++
            }
            if (accounts.isNotEmpty()) {
                ipPrefs.replaceAllAccounts(accounts, payload.optString("activeSipAccountId", accounts.first().id))
            }
        }

        return "Restored settings, $notesCount notes & $sipCount SIP account(s)"
    }
}
