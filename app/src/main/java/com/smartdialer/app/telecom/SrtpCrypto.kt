package com.smartdialer.app.telecom

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Kotlin RFC 3711 SRTP implementation supporting AES_CM_128_HMAC_SHA1_80.
 * Uses Android's built-in JCE AES/CTR/NoPadding and HmacSHA1 with zero external dependencies.
 */
class SrtpSessionCrypto(
    val masterKeyAndSalt: ByteArray // 16-byte master key + 14-byte master salt = 30 bytes
) {
    private val encKey: ByteArray
    private val authKey: ByteArray
    private val saltKey: ByteArray

    companion object {
        private const val MASTER_KEY_LEN = 16
        private const val MASTER_SALT_LEN = 14
        const val AUTH_TAG_LEN = 10 // 80-bit tag

        fun generateRandom(): SrtpSessionCrypto {
            val bytes = ByteArray(MASTER_KEY_LEN + MASTER_SALT_LEN)
            SecureRandom().nextBytes(bytes)
            return SrtpSessionCrypto(bytes)
        }

        fun fromInlineBase64(inlineKey: String): SrtpSessionCrypto? {
            return try {
                val decoded = Base64.decode(inlineKey.trim(), Base64.DEFAULT)
                if (decoded.size >= MASTER_KEY_LEN + MASTER_SALT_LEN) {
                    SrtpSessionCrypto(decoded.copyOf(MASTER_KEY_LEN + MASTER_SALT_LEN))
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    init {
        val mKey = masterKeyAndSalt.copyOfRange(0, MASTER_KEY_LEN)
        val mSalt = masterKeyAndSalt.copyOfRange(MASTER_KEY_LEN, MASTER_KEY_LEN + MASTER_SALT_LEN)
        encKey = deriveKey(mKey, mSalt, label = 0x00, outLen = 16)
        authKey = deriveKey(mKey, mSalt, label = 0x01, outLen = 20)
        saltKey = deriveKey(mKey, mSalt, label = 0x02, outLen = 14)
    }

    fun toInlineBase64(): String =
        Base64.encodeToString(masterKeyAndSalt, Base64.NO_WRAP)

    /**
     * Encrypts an RTP packet in place and appends the 10-byte HMAC-SHA1-80 authentication tag.
     */
    fun protect(rtpPacket: ByteArray, rtpLen: Int, roc: Int = 0): ByteArray {
        if (rtpLen < 12) return rtpPacket.copyOf(rtpLen)
        val headerLen = computeHeaderLen(rtpPacket, rtpLen)
        if (rtpLen < headerLen) return rtpPacket.copyOf(rtpLen)

        val seq = ((rtpPacket[2].toInt() and 0xFF) shl 8) or (rtpPacket[3].toInt() and 0xFF)
        val ssrc = ((rtpPacket[8].toLong() and 0xFF) shl 24) or
            ((rtpPacket[9].toLong() and 0xFF) shl 16) or
            ((rtpPacket[10].toLong() and 0xFF) shl 8) or
            (rtpPacket[11].toLong() and 0xFF)

        val index = ((roc.toLong() and 0xFFFFFFFFL) shl 16) or (seq.toLong() and 0xFFFFL)
        val iv = computeRtpIv(ssrc, index)

        val payloadLen = rtpLen - headerLen
        val encryptedPayload = if (payloadLen > 0) {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            cipher.doFinal(rtpPacket, headerLen, payloadLen)
        } else {
            ByteArray(0)
        }

        val out = ByteArray(rtpLen + AUTH_TAG_LEN)
        System.arraycopy(rtpPacket, 0, out, 0, headerLen)
        if (payloadLen > 0) {
            System.arraycopy(encryptedPayload, 0, out, headerLen, payloadLen)
        }

        val tag = computeAuthTag(out, rtpLen, roc)
        System.arraycopy(tag, 0, out, rtpLen, AUTH_TAG_LEN)
        return out
    }

    /**
     * Verifies and decrypts an incoming SRTP packet. Returns decrypted RTP bytes, or null if auth fails.
     */
    fun unprotect(srtpPacket: ByteArray, srtpLen: Int, roc: Int = 0): ByteArray? {
        if (srtpLen < 12 + AUTH_TAG_LEN) return null
        val rtpLen = srtpLen - AUTH_TAG_LEN
        val headerLen = computeHeaderLen(srtpPacket, rtpLen)
        if (rtpLen < headerLen) return null

        val expectedTag = computeAuthTag(srtpPacket, rtpLen, roc)
        var mismatch = 0
        for (i in 0 until AUTH_TAG_LEN) {
            mismatch = mismatch or (expectedTag[i].toInt() xor srtpPacket[rtpLen + i].toInt())
        }
        if (mismatch != 0) return null

        val seq = ((srtpPacket[2].toInt() and 0xFF) shl 8) or (srtpPacket[3].toInt() and 0xFF)
        val ssrc = ((srtpPacket[8].toLong() and 0xFF) shl 24) or
            ((srtpPacket[9].toLong() and 0xFF) shl 16) or
            ((srtpPacket[10].toLong() and 0xFF) shl 8) or
            (srtpPacket[11].toLong() and 0xFF)

        val index = ((roc.toLong() and 0xFFFFFFFFL) shl 16) or (seq.toLong() and 0xFFFFL)
        val iv = computeRtpIv(ssrc, index)

        val payloadLen = rtpLen - headerLen
        val decryptedPayload = if (payloadLen > 0) {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            cipher.doFinal(srtpPacket, headerLen, payloadLen)
        } else {
            ByteArray(0)
        }

        val out = ByteArray(rtpLen)
        System.arraycopy(srtpPacket, 0, out, 0, headerLen)
        if (payloadLen > 0) {
            System.arraycopy(decryptedPayload, 0, out, headerLen, payloadLen)
        }
        return out
    }

    private fun computeHeaderLen(packet: ByteArray, len: Int): Int {
        val cc = packet[0].toInt() and 0x0F
        var hLen = 12 + (cc * 4)
        val hasExt = (packet[0].toInt() and 0x10) != 0
        if (hasExt && len >= hLen + 4) {
            val extWords = ((packet[hLen + 2].toInt() and 0xFF) shl 8) or (packet[hLen + 3].toInt() and 0xFF)
            hLen += 4 + (extWords * 4)
        }
        return hLen
    }

    private fun computeRtpIv(ssrc: Long, index: Long): ByteArray {
        val iv = ByteArray(16)
        System.arraycopy(saltKey, 0, iv, 0, 14)
        iv[4] = (iv[4].toInt() xor ((ssrc shr 24) and 0xFF).toInt()).toByte()
        iv[5] = (iv[5].toInt() xor ((ssrc shr 16) and 0xFF).toInt()).toByte()
        iv[6] = (iv[6].toInt() xor ((ssrc shr 8) and 0xFF).toInt()).toByte()
        iv[7] = (iv[7].toInt() xor (ssrc and 0xFF).toInt()).toByte()

        for (i in 0 until 6) {
            val shift = (5 - i) * 8
            iv[8 + i] = (iv[8 + i].toInt() xor ((index shr shift) and 0xFF).toInt()).toByte()
        }
        return iv
    }

    private fun computeAuthTag(packet: ByteArray, rtpLen: Int, roc: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(authKey, "HmacSHA1"))
        mac.update(packet, 0, rtpLen)
        val rocBytes = ByteBuffer.allocate(4).putInt(roc).array()
        mac.update(rocBytes)
        return mac.doFinal().copyOf(AUTH_TAG_LEN)
    }

    private fun deriveKey(masterKey: ByteArray, masterSalt: ByteArray, label: Int, outLen: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"))

        val out = ByteArray(outLen)
        val blocks = (outLen + 15) / 16
        for (block in 0 until blocks) {
            val x = ByteArray(16)
            System.arraycopy(masterSalt, 0, x, 0, 14)
            x[7] = (x[7].toInt() xor (label and 0xFF)).toByte()
            x[15] = (x[15].toInt() xor (block and 0xFF)).toByte()
            val keyBlock = cipher.doFinal(x)
            val copyLen = minOf(16, outLen - block * 16)
            System.arraycopy(keyBlock, 0, out, block * 16, copyLen)
        }
        return out
    }
}
