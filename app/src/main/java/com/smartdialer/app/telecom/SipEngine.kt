package com.smartdialer.app.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.data.repository.IpCallPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * ITU-T Recommendation G.711 Audio Codec (u-Law and A-Law).
 * Provides fast, zero-allocation 8kHz 16-bit PCM <-> 8-bit log compression for VoIP.
 */
object G711 {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun linearToUlaw(sample: Short): Byte {
        var pcm = sample.toInt()
        val sign = (pcm shr 8) and 0x80
        if (sign != 0) pcm = -pcm
        if (pcm > CLIP) pcm = CLIP
        pcm += BIAS

        var exponent = 7
        var expMask = 0x4000
        while ((pcm and expMask) == 0 && exponent > 0) {
            exponent--
            expMask = expMask shr 1
        }
        val mantissa = (pcm shr (exponent + 3)) and 0x0F
        val ulaw = (sign or (exponent shl 4) or mantissa).inv()
        return ulaw.toByte()
    }

    fun ulawToLinear(ulawByte: Byte): Short {
        val ulaw = (ulawByte.toInt().inv()) and 0xFF
        val sign = ulaw and 0x80
        val exponent = (ulaw shr 4) and 0x07
        val mantissa = ulaw and 0x0F
        var sample = ((mantissa shl 3) + BIAS) shl exponent
        sample -= BIAS
        return (if (sign != 0) -sample else sample).toShort()
    }

    fun linearToAlaw(sample: Short): Byte {
        var pcm = sample.toInt()
        val sign = (pcm shr 8) and 0x80
        if (sign != 0) pcm = -pcm
        if (pcm > 32767) pcm = 32767

        val alaw = if (pcm >= 256) {
            var exponent = 7
            var expMask = 0x4000
            while ((pcm and expMask) == 0 && exponent > 1) {
                exponent--
                expMask = expMask shr 1
            }
            val mantissa = (pcm shr (exponent + 3)) and 0x0F
            sign or (exponent shl 4) or mantissa
        } else {
            sign or (pcm shr 4)
        }
        return (alaw xor 0xD5).toByte()
    }

    fun alawToLinear(alawByte: Byte): Short {
        val alaw = (alawByte.toInt() xor 0xD5) and 0xFF
        val sign = alaw and 0x80
        val exponent = (alaw shr 4) and 0x07
        val mantissa = alaw and 0x0F
        val sample = if (exponent == 0) {
            (mantissa shl 4) + 8
        } else {
            ((mantissa shl 4) + 0x108) shl (exponent - 1)
        }
        return (if (sign != 0) -sample else sample).toShort()
    }
}

/**
 * Parsed SIP Message representation.
 */
data class SipMessage(
    val raw: String,
    val isResponse: Boolean,
    val statusCode: Int = 0,
    val statusReason: String = "",
    val method: String = "",
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
) {
    fun getHeader(name: String): String? = headers[name.lowercase()]

    companion object {
        fun parse(raw: String): SipMessage? {
            if (raw.isBlank()) return null
            val parts = raw.split("\r\n\r\n", "\n\n", limit = 2)
            val headerBlock = parts[0]
            val body = if (parts.size > 1) parts[1] else ""
            val lines = headerBlock.split("\r\n", "\n")
            if (lines.isEmpty()) return null

            val startLine = lines[0].trim()
            var isResponse = false
            var statusCode = 0
            var statusReason = ""
            var method = ""

            if (startLine.startsWith("SIP/2.0")) {
                isResponse = true
                val tokens = startLine.split(" ", limit = 3)
                if (tokens.size >= 2) {
                    statusCode = tokens[1].toIntOrNull() ?: 0
                    statusReason = if (tokens.size >= 3) tokens[2] else ""
                }
            } else {
                val tokens = startLine.split(" ", limit = 3)
                if (tokens.isNotEmpty()) {
                    method = tokens[0].uppercase()
                }
            }

            val headers = mutableMapOf<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            return SipMessage(
                raw = raw,
                isResponse = isResponse,
                statusCode = statusCode,
                statusReason = statusReason,
                method = method,
                headers = headers,
                body = body
            )
        }
    }
}

/**
 * Pure Kotlin SIP & RTP Engine (RFC 3261, RFC 2617, RFC 3550, RFC 4566, G.711).
 * Directly connects IPTSP / VoIP accounts to make and receive calls with in-app audio.
 */
object SipEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _registrationStatus = MutableStateFlow("Offline")
    val registrationStatus: StateFlow<String> = _registrationStatus.asStateFlow()

    private val isCallActive = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isSpeakerOn = AtomicBoolean(false)

    // Active Call State Variables
    private var activeCallId: String? = null
    private var fromTag: String? = null
    private var toTag: String? = null
    private var currentCSeq = 1
    private var destinationNumber = ""
    private var sipDomain = ""
    private var contactUri: String? = null

    // Audio & RTP State Variables
    private var remoteRtpIp: String? = null
    private var remoteRtpPort: Int = 0
    private var negotiatedCodec: Int = 0 // 0 = PCMU, 8 = PCMA
    private var localRtpPort = 40002

    private var sipSocket: DatagramSocket? = null
    private var rtpSocket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var aecEffect: AcousticEchoCanceler? = null
    private var nsEffect: NoiseSuppressor? = null

    private var callListenerJob: Job? = null
    private var rtpSendJob: Job? = null
    private var rtpReceiveJob: Job? = null
    private var keepAliveJob: Job? = null

    private var onCallStateChanged: ((CallStatus, String?) -> Unit)? = null

    fun isCallRunning(): Boolean = isCallActive.get()

    /**
     * Finds local non-loopback IPv4 address.
     */
    fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && host != "127.0.0.1") {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    private fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseAuthChallenge(headerValue: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]+))""")
        regex.findAll(headerValue).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            result[key] = value
        }
        return result
    }

    private fun calculateDigestResponse(
        username: String,
        realm: String,
        password: String,
        method: String,
        uri: String,
        nonce: String,
        qop: String? = null,
        nc: String = "00000001",
        cnonce: String = "0a4f113b"
    ): String {
        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("$method:$uri")
        return if (qop != null && qop.contains("auth")) {
            md5Hex("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }
    }

    /**
     * Tests SIP Registration with the configured account.
     */
    fun registerAccount(context: Context, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val prefs = IpCallPreferences(context)
            val user = prefs.username.trim()
            val pass = prefs.password
            val domain = if (prefs.domain.isNotBlank()) prefs.domain.trim() else prefs.outboundProxy.trim()
            val authUser = if (prefs.authName.isNotBlank()) prefs.authName.trim() else user

            if (user.isBlank() || domain.isBlank()) {
                withContext(Dispatchers.Main) {
                    _registrationStatus.value = "Not Configured"
                    onResult(false, "Username and domain are required")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _registrationStatus.value = "Connecting..."
            }

            var testSocket: DatagramSocket? = null
            try {
                testSocket = DatagramSocket()
                testSocket.soTimeout = 4000
                val localIp = getLocalIp()
                val localPort = testSocket.localPort
                val callId = UUID.randomUUID().toString()
                val tag = UUID.randomUUID().toString().replace("-", "").take(8)
                val targetAddr = InetAddress.getByName(domain)

                // 1. Initial REGISTER
                val branch1 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                val reg1 = buildString {
                    append("REGISTER sip:$domain SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch1;rport\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <sip:$user@$domain>;tag=$tag\r\n")
                    append("To: <sip:$user@$domain>\r\n")
                    append("Call-ID: $callId@$localIp\r\n")
                    append("CSeq: 1 REGISTER\r\n")
                    append("Contact: <sip:$user@$localIp:$localPort;transport=udp>\r\n")
                    append("Expires: 300\r\n")
                    append("User-Agent: SmartDialer/1.0\r\n")
                    append("Content-Length: 0\r\n\r\n")
                }

                val packet1 = DatagramPacket(reg1.toByteArray(), reg1.length, targetAddr, 5060)
                testSocket.send(packet1)

                val buffer = ByteArray(4096)
                val recvPacket = DatagramPacket(buffer, buffer.size)
                testSocket.receive(recvPacket)
                val respText1 = String(recvPacket.data, 0, recvPacket.length)
                val msg1 = SipMessage.parse(respText1)

                if (msg1 == null) {
                    withContext(Dispatchers.Main) {
                        _registrationStatus.value = "Offline"
                        onResult(false, "Invalid SIP response from server")
                    }
                    return@launch
                }

                if (msg1.statusCode == 200) {
                    withContext(Dispatchers.Main) {
                        _registrationStatus.value = "Online / Registered"
                        onResult(true, "SIP Account Connected Successfully!")
                    }
                    return@launch
                }

                if (msg1.statusCode == 401 || msg1.statusCode == 407) {
                    val authHeader = msg1.getHeader("www-authenticate") ?: msg1.getHeader("proxy-authenticate") ?: ""
                    val challenge = parseAuthChallenge(authHeader)
                    val realm = challenge["realm"] ?: domain
                    val nonce = challenge["nonce"] ?: ""
                    val qop = challenge["qop"]
                    val nc = "00000001"
                    val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
                    val digest = calculateDigestResponse(authUser, realm, pass, "REGISTER", "sip:$domain", nonce, qop, nc, cnonce)

                    val authHeaderName = if (msg1.statusCode == 407) "Proxy-Authorization" else "Authorization"
                    val authValue = buildString {
                        append("Digest username=\"$authUser\", realm=\"$realm\", nonce=\"$nonce\", uri=\"sip:$domain\", response=\"$digest\", algorithm=MD5")
                        if (qop != null && qop.contains("auth")) {
                            append(", cnonce=\"$cnonce\", nc=$nc, qop=auth")
                        }
                    }

                    // 2. Authenticated REGISTER
                    val branch2 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                    val reg2 = buildString {
                        append("REGISTER sip:$domain SIP/2.0\r\n")
                        append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch2;rport\r\n")
                        append("Max-Forwards: 70\r\n")
                        append("From: <sip:$user@$domain>;tag=$tag\r\n")
                        append("To: <sip:$user@$domain>\r\n")
                        append("Call-ID: $callId@$localIp\r\n")
                        append("CSeq: 2 REGISTER\r\n")
                        append("Contact: <sip:$user@$localIp:$localPort;transport=udp>\r\n")
                        append("$authHeaderName: $authValue\r\n")
                        append("Expires: 300\r\n")
                        append("User-Agent: SmartDialer/1.0\r\n")
                        append("Content-Length: 0\r\n\r\n")
                    }

                    val packet2 = DatagramPacket(reg2.toByteArray(), reg2.length, targetAddr, 5060)
                    testSocket.send(packet2)

                    testSocket.receive(recvPacket)
                    val respText2 = String(recvPacket.data, 0, recvPacket.length)
                    val msg2 = SipMessage.parse(respText2)

                    if (msg2?.statusCode == 200) {
                        withContext(Dispatchers.Main) {
                            _registrationStatus.value = "Online / Registered"
                            onResult(true, "SIP Account Connected Successfully!")
                        }
                    } else {
                        val reason = msg2?.statusReason ?: "Authentication Failed (${msg2?.statusCode})"
                        withContext(Dispatchers.Main) {
                            _registrationStatus.value = "Auth Failed"
                            onResult(false, reason)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _registrationStatus.value = "Failed (${msg1.statusCode})"
                        onResult(false, msg1.statusReason)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _registrationStatus.value = "Offline"
                    onResult(false, "Connection error: ${e.localizedMessage ?: "Timeout"}")
                }
            } finally {
                testSocket?.close()
            }
        }
    }

    /**
     * Starts an outgoing IP/SIP Call to destination number.
     */
    fun startCall(
        destination: String,
        context: Context,
        onStateChange: (CallStatus, String?) -> Unit
    ) {
        if (isCallActive.get()) {
            endCall()
        }

        onCallStateChanged = onStateChange
        destinationNumber = destination.trim().replace(" ", "").replace("-", "")

        val prefs = IpCallPreferences(context)
        val user = prefs.username.trim()
        val pass = prefs.password
        val domain = if (prefs.domain.isNotBlank()) prefs.domain.trim() else prefs.outboundProxy.trim()
        val authUser = if (prefs.authName.isNotBlank()) prefs.authName.trim() else user
        sipDomain = domain

        if (user.isBlank() || domain.isBlank()) {
            onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "SIP account not configured")
            return
        }

        isCallActive.set(true)
        isMuted.set(false)
        isSpeakerOn.set(false)
        currentCSeq = 1
        activeCallId = UUID.randomUUID().toString() + "@" + getLocalIp()
        fromTag = UUID.randomUUID().toString().replace("-", "").take(8)
        toTag = null
        contactUri = null

        // Configure Audio Manager for VoIP
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = false
        audioManager?.isMicrophoneMute = false

        onCallStateChanged?.invoke(CallStatus.CONNECTING, "Connecting IP Call...")

        scope.launch {
            try {
                // Setup Sockets
                sipSocket = DatagramSocket()
                sipSocket?.soTimeout = 25000

                // Pick an even port for RTP
                localRtpPort = (40000..50000).random()
                if (localRtpPort % 2 != 0) localRtpPort++
                rtpSocket = DatagramSocket(localRtpPort)
                rtpSocket?.soTimeout = 1000

                val localIp = getLocalIp()
                val localSipPort = sipSocket?.localPort ?: 5060
                val serverAddr = InetAddress.getByName(domain)

                // Build initial SDP Offer (PCMU 0, PCMA 8, DTMF 101)
                val sdpOffer = buildString {
                    append("v=0\r\n")
                    append("o=- ${System.currentTimeMillis()} 1 IN IP4 $localIp\r\n")
                    append("s=SmartDialer\r\n")
                    append("c=IN IP4 $localIp\r\n")
                    append("t=0 0\r\n")
                    append("m=audio $localRtpPort RTP/AVP 0 8 101\r\n")
                    append("a=rtpmap:0 PCMU/8000\r\n")
                    append("a=rtpmap:8 PCMA/8000\r\n")
                    append("a=rtpmap:101 telephone-event/8000\r\n")
                    append("a=fmtp:101 0-16\r\n")
                    append("a=sendrecv\r\n")
                }

                // 1. Initial INVITE
                val branch1 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                val invite1 = buildString {
                    append("INVITE sip:$destinationNumber@$domain SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$branch1;rport\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <sip:$user@$domain>;tag=$fromTag\r\n")
                    append("To: <sip:$destinationNumber@$domain>\r\n")
                    append("Call-ID: $activeCallId\r\n")
                    append("CSeq: 1 INVITE\r\n")
                    append("Contact: <sip:$user@$localIp:$localSipPort;transport=udp>\r\n")
                    append("User-Agent: SmartDialer/1.0\r\n")
                    append("Content-Type: application/sdp\r\n")
                    append("Content-Length: ${sdpOffer.length}\r\n\r\n")
                    append(sdpOffer)
                }

                val packet1 = DatagramPacket(invite1.toByteArray(), invite1.length, serverAddr, 5060)
                sipSocket?.send(packet1)

                // SIP Signaling Loop
                val buffer = ByteArray(4096)
                var callEstablished = false

                while (isActive && isCallActive.get() && !callEstablished) {
                    val recvPacket = DatagramPacket(buffer, buffer.size)
                    try {
                        sipSocket?.receive(recvPacket)
                    } catch (e: Exception) {
                        if (!isCallActive.get()) break
                        continue
                    }

                    val respText = String(recvPacket.data, 0, recvPacket.length)
                    val msg = SipMessage.parse(respText) ?: continue

                    if (msg.isResponse) {
                        when (msg.statusCode) {
                            100 -> {
                                withContext(Dispatchers.Main) {
                                    onCallStateChanged?.invoke(CallStatus.CONNECTING, "Connecting...")
                                }
                            }
                            180, 183 -> {
                                withContext(Dispatchers.Main) {
                                    onCallStateChanged?.invoke(CallStatus.RINGING, "Ringing...")
                                }
                                // If 183 includes early media SDP
                                if (msg.body.contains("m=audio")) {
                                    extractRemoteSdp(msg.body, domain)
                                    startRtpAudio(context)
                                }
                            }
                            401, 407 -> {
                                // Send ACK for 401/407
                                val ackMsg = buildString {
                                    append("ACK sip:$destinationNumber@$domain SIP/2.0\r\n")
                                    append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$branch1;rport\r\n")
                                    append("Max-Forwards: 70\r\n")
                                    append("From: <sip:$user@$domain>;tag=$fromTag\r\n")
                                    append("To: <sip:$destinationNumber@$domain>\r\n")
                                    append("Call-ID: $activeCallId\r\n")
                                    append("CSeq: 1 ACK\r\n")
                                    append("Content-Length: 0\r\n\r\n")
                                }
                                val ackPacket = DatagramPacket(ackMsg.toByteArray(), ackMsg.length, serverAddr, 5060)
                                sipSocket?.send(ackPacket)

                                // Challenge Response
                                val authHeader = msg.getHeader("www-authenticate") ?: msg.getHeader("proxy-authenticate") ?: ""
                                val challenge = parseAuthChallenge(authHeader)
                                val realm = challenge["realm"] ?: domain
                                val nonce = challenge["nonce"] ?: ""
                                val qop = challenge["qop"]
                                val nc = "00000001"
                                val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
                                val uriTarget = "sip:$destinationNumber@$domain"
                                val digest = calculateDigestResponse(authUser, realm, pass, "INVITE", uriTarget, nonce, qop, nc, cnonce)

                                val authHeaderName = if (msg.statusCode == 407) "Proxy-Authorization" else "Authorization"
                                val authValue = buildString {
                                    append("Digest username=\"$authUser\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uriTarget\", response=\"$digest\", algorithm=MD5")
                                    if (qop != null && qop.contains("auth")) {
                                        append(", cnonce=\"$cnonce\", nc=$nc, qop=auth")
                                    }
                                }

                                currentCSeq = 2
                                val branch2 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                                val invite2 = buildString {
                                    append("INVITE sip:$destinationNumber@$domain SIP/2.0\r\n")
                                    append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$branch2;rport\r\n")
                                    append("Max-Forwards: 70\r\n")
                                    append("From: <sip:$user@$domain>;tag=$fromTag\r\n")
                                    append("To: <sip:$destinationNumber@$domain>\r\n")
                                    append("Call-ID: $activeCallId\r\n")
                                    append("CSeq: 2 INVITE\r\n")
                                    append("Contact: <sip:$user@$localIp:$localSipPort;transport=udp>\r\n")
                                    append("$authHeaderName: $authValue\r\n")
                                    append("User-Agent: SmartDialer/1.0\r\n")
                                    append("Content-Type: application/sdp\r\n")
                                    append("Content-Length: ${sdpOffer.length}\r\n\r\n")
                                    append(sdpOffer)
                                }

                                val packet2 = DatagramPacket(invite2.toByteArray(), invite2.length, serverAddr, 5060)
                                sipSocket?.send(packet2)
                            }
                            200 -> {
                                callEstablished = true
                                // Extract server To Tag
                                val toHeader = msg.getHeader("to") ?: ""
                                val tagMatch = Regex("""tag=([^;,\s]+)""").find(toHeader)
                                toTag = tagMatch?.groupValues?.get(1)

                                // Extract Contact URI for direct ACK / BYE
                                contactUri = msg.getHeader("contact")?.let { rawContact ->
                                    val match = Regex("""<([^>]+)>""").find(rawContact)
                                    match?.groupValues?.get(1) ?: rawContact
                                }

                                extractRemoteSdp(msg.body, domain)

                                // Send ACK for 200 OK
                                val ackBranch = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                                val ackTarget = contactUri ?: "sip:$destinationNumber@$domain"
                                val ackMsg = buildString {
                                    append("ACK $ackTarget SIP/2.0\r\n")
                                    append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$ackBranch;rport\r\n")
                                    append("Max-Forwards: 70\r\n")
                                    append("From: <sip:$user@$domain>;tag=$fromTag\r\n")
                                    append("To: <sip:$destinationNumber@$domain>${if (toTag != null) ";tag=$toTag" else ""}\r\n")
                                    append("Call-ID: $activeCallId\r\n")
                                    append("CSeq: $currentCSeq ACK\r\n")
                                    append("Contact: <sip:$user@$localIp:$localSipPort;transport=udp>\r\n")
                                    append("Content-Length: 0\r\n\r\n")
                                }
                                val ackPacket = DatagramPacket(ackMsg.toByteArray(), ackMsg.length, serverAddr, 5060)
                                sipSocket?.send(ackPacket)

                                withContext(Dispatchers.Main) {
                                    onCallStateChanged?.invoke(CallStatus.ACTIVE, null)
                                }

                                startRtpAudio(context)
                                startSipCallListener(domain)
                            }
                            486 -> {
                                withContext(Dispatchers.Main) {
                                    onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "Busy")
                                }
                                endCall()
                                break
                            }
                            404 -> {
                                withContext(Dispatchers.Main) {
                                    onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "Number not found")
                                }
                                endCall()
                                break
                            }
                            in 400..699 -> {
                                withContext(Dispatchers.Main) {
                                    onCallStateChanged?.invoke(CallStatus.DISCONNECTED, msg.statusReason)
                                }
                                endCall()
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onCallStateChanged?.invoke(CallStatus.DISCONNECTED, e.localizedMessage ?: "Call error")
                }
                endCall()
            }
        }
    }

    private fun extractRemoteSdp(sdp: String, fallbackHost: String) {
        // Find Remote IP: c=IN IP4 1.2.3.4
        val ipMatch = Regex("""c=IN IP4\s+([0-9.]+)""").find(sdp)
        remoteRtpIp = ipMatch?.groupValues?.get(1) ?: fallbackHost

        // Find Remote Port & Codec: m=audio 16428 RTP/AVP 0 8 101
        val audioMatch = Regex("""m=audio\s+(\d+)\s+RTP/AVP\s+([0-9\s]+)""").find(sdp)
        if (audioMatch != null) {
            remoteRtpPort = audioMatch.groupValues[1].toIntOrNull() ?: 16428
            val codecs = audioMatch.groupValues[2].trim().split(Regex("""\s+"""))
            negotiatedCodec = if (codecs.contains("8") && !codecs.contains("0")) 8 else 0
        }
    }

    private fun startSipCallListener(domain: String) {
        callListenerJob?.cancel()
        callListenerJob = scope.launch {
            val buffer = ByteArray(2048)
            val serverAddr = InetAddress.getByName(domain)
            while (isActive && isCallActive.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    sipSocket?.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    val msg = SipMessage.parse(text) ?: continue

                    // If remote party sends BYE
                    if (msg.method == "BYE") {
                        // Send 200 OK to acknowledge BYE
                        val resp = buildString {
                            append("SIP/2.0 200 OK\r\n")
                            msg.getHeader("via")?.let { append("Via: $it\r\n") }
                            msg.getHeader("from")?.let { append("From: $it\r\n") }
                            msg.getHeader("to")?.let { append("To: $it\r\n") }
                            msg.getHeader("call-id")?.let { append("Call-ID: $it\r\n") }
                            msg.getHeader("cseq")?.let { append("CSeq: $it\r\n") }
                            append("Content-Length: 0\r\n\r\n")
                        }
                        val respPacket = DatagramPacket(resp.toByteArray(), resp.length, serverAddr, 5060)
                        sipSocket?.send(respPacket)

                        withContext(Dispatchers.Main) {
                            onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "Call Ended")
                        }
                        endCall()
                        break
                    }
                } catch (e: Exception) {
                    // Socket timeout or closed
                }
            }
        }
    }

    /**
     * Bidirectional G.711 RTP Audio Stream (Microphone -> PCMU -> UDP Send, UDP Recv -> PCMU -> Speaker).
     */
    private fun startRtpAudio(context: Context) {
        if (rtpSendJob != null && rtpSendJob?.isActive == true) return

        val sampleRate = 8000
        val pcmFrameSize = 160 // 20ms at 8kHz

        // 1. Initialize AudioRecord (with Hardware AEC and Noise Suppression)
        val minRecBuf = max(
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            1280
        )
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf
            )
            audioRecord?.let { rec ->
                if (AcousticEchoCanceler.isAvailable()) {
                    aecEffect = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    nsEffect = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
                }
                rec.startRecording()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Initialize AudioTrack for Playback
        val minTrackBuf = max(
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
            1280
        )
        try {
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                minTrackBuf,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. RTP Transmission Loop (Mic -> G.711 -> Remote)
        rtpSendJob = scope.launch(Dispatchers.IO) {
            val pcmBuf = ShortArray(pcmFrameSize)
            val rtpPacketData = ByteArray(12 + pcmFrameSize)
            var seq = 0
            var timestamp = 0L
            val ssrc = (100000..999999).random().toLong()

            // Header constants
            rtpPacketData[0] = 0x80.toByte() // V=2, P=0, X=0, CC=0
            rtpPacketData[1] = (negotiatedCodec and 0x7F).toByte()
            rtpPacketData[8] = ((ssrc shr 24) and 0xFF).toByte()
            rtpPacketData[9] = ((ssrc shr 16) and 0xFF).toByte()
            rtpPacketData[10] = ((ssrc shr 8) and 0xFF).toByte()
            rtpPacketData[11] = (ssrc and 0xFF).toByte()

            while (isActive && isCallActive.get()) {
                val rec = audioRecord ?: break
                val readCount = rec.read(pcmBuf, 0, pcmFrameSize)
                if (readCount <= 0) continue

                // Handle Mute: Zero out PCM samples if muted
                if (isMuted.get()) {
                    for (i in 0 until readCount) {
                        pcmBuf[i] = 0
                    }
                }

                // G.711 Encoding
                if (negotiatedCodec == 8) {
                    for (i in 0 until readCount) {
                        rtpPacketData[12 + i] = G711.linearToAlaw(pcmBuf[i])
                    }
                } else {
                    for (i in 0 until readCount) {
                        rtpPacketData[12 + i] = G711.linearToUlaw(pcmBuf[i])
                    }
                }

                // Sequence number & timestamp
                rtpPacketData[2] = ((seq shr 8) and 0xFF).toByte()
                rtpPacketData[3] = (seq and 0xFF).toByte()
                rtpPacketData[4] = ((timestamp shr 24) and 0xFF).toByte()
                rtpPacketData[5] = ((timestamp shr 16) and 0xFF).toByte()
                rtpPacketData[6] = ((timestamp shr 8) and 0xFF).toByte()
                rtpPacketData[7] = (timestamp and 0xFF).toByte()

                seq = (seq + 1) and 0xFFFF
                timestamp += pcmFrameSize

                val targetIp = remoteRtpIp
                if (targetIp != null && remoteRtpPort > 0) {
                    try {
                        val packet = DatagramPacket(
                            rtpPacketData,
                            rtpPacketData.size,
                            InetAddress.getByName(targetIp),
                            remoteRtpPort
                        )
                        rtpSocket?.send(packet)
                    } catch (e: Exception) {
                        // ignore network hiccup
                    }
                }
            }
        }

        // 4. RTP Reception Loop (Remote -> G.711 Decode -> AudioTrack)
        rtpReceiveJob = scope.launch(Dispatchers.IO) {
            val recvBuffer = ByteArray(1500)
            val pcmOut = ShortArray(pcmFrameSize)

            while (isActive && isCallActive.get()) {
                val sock = rtpSocket ?: break
                val packet = DatagramPacket(recvBuffer, recvBuffer.size)
                try {
                    sock.receive(packet)
                } catch (e: Exception) {
                    continue
                }

                if (packet.length < 12) continue

                // Comedia / Symmetric RTP auto-adaptation (if PBX sends audio from a different port behind NAT)
                if (remoteRtpIp == null || remoteRtpPort == 0) {
                    remoteRtpIp = packet.address.hostAddress
                    remoteRtpPort = packet.port
                }

                val payloadLen = packet.length - 12
                if (payloadLen <= 0) continue

                val outLen = if (payloadLen > pcmOut.size) pcmOut.size else payloadLen
                if (negotiatedCodec == 8) {
                    for (i in 0 until outLen) {
                        pcmOut[i] = G711.alawToLinear(recvBuffer[12 + i])
                    }
                } else {
                    for (i in 0 until outLen) {
                        pcmOut[i] = G711.ulawToLinear(recvBuffer[12 + i])
                    }
                }

                audioTrack?.write(pcmOut, 0, outLen)
            }
        }
    }

    /**
     * Sends DTMF digit over SIP (INFO application/dtmf-relay).
     */
    fun sendDtmf(digit: Char) {
        scope.launch {
            val domain = sipDomain
            val callId = activeCallId
            val from = fromTag
            val target = destinationNumber
            if (domain.isBlank() || callId.isNullOrBlank() || from.isNullOrBlank()) return@launch

            val targetUri = contactUri ?: "sip:$target@$domain"
            val localIp = getLocalIp()
            val localPort = sipSocket?.localPort ?: 5060
            val branch = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)

            val body = "Signal=$digit\r\nDuration=160\r\n"
            val info = buildString {
                append("INFO $targetUri SIP/2.0\r\n")
                append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch;rport\r\n")
                append("Max-Forwards: 70\r\n")
                append("From: <sip:user@$domain>;tag=$from\r\n")
                append("To: <sip:$target@$domain>${if (toTag != null) ";tag=$toTag" else ""}\r\n")
                append("Call-ID: $callId\r\n")
                append("CSeq: ${++currentCSeq} INFO\r\n")
                append("Content-Type: application/dtmf-relay\r\n")
                append("Content-Length: ${body.length}\r\n\r\n")
                append(body)
            }

            try {
                val serverAddr = InetAddress.getByName(domain)
                val packet = DatagramPacket(info.toByteArray(), info.length, serverAddr, 5060)
                sipSocket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun setSpeaker(context: Context, speaker: Boolean) {
        isSpeakerOn.set(speaker)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.isSpeakerphoneOn = speaker
    }

    /**
     * Ends active or dialing call. Sends BYE or CANCEL.
     */
    fun endCall() {
        if (!isCallActive.getAndSet(false)) return

        scope.launch {
            val domain = sipDomain
            val callId = activeCallId
            val from = fromTag
            val target = destinationNumber

            if (domain.isNotBlank() && !callId.isNullOrBlank() && !from.isNullOrBlank()) {
                val localIp = getLocalIp()
                val localPort = sipSocket?.localPort ?: 5060
                val serverAddr = try { InetAddress.getByName(domain) } catch (e: Exception) { null }

                if (serverAddr != null) {
                    val branch = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                    if (toTag != null) {
                        // Call was active -> send BYE
                        val byeTarget = contactUri ?: "sip:$target@$domain"
                        val byeMsg = buildString {
                            append("BYE $byeTarget SIP/2.0\r\n")
                            append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch;rport\r\n")
                            append("Max-Forwards: 70\r\n")
                            append("From: <sip:user@$domain>;tag=$from\r\n")
                            append("To: <sip:$target@$domain>;tag=$toTag\r\n")
                            append("Call-ID: $callId\r\n")
                            append("CSeq: ${++currentCSeq} BYE\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        }
                        try {
                            val packet = DatagramPacket(byeMsg.toByteArray(), byeMsg.length, serverAddr, 5060)
                            sipSocket?.send(packet)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        // Call was connecting/dialing -> send CANCEL
                        val cancelMsg = buildString {
                            append("CANCEL sip:$target@$domain SIP/2.0\r\n")
                            append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch;rport\r\n")
                            append("Max-Forwards: 70\r\n")
                            append("From: <sip:user@$domain>;tag=$from\r\n")
                            append("To: <sip:$target@$domain>\r\n")
                            append("Call-ID: $callId\r\n")
                            append("CSeq: 1 CANCEL\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        }
                        try {
                            val packet = DatagramPacket(cancelMsg.toByteArray(), cancelMsg.length, serverAddr, 5060)
                            sipSocket?.send(packet)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Teardown Audio & Sockets
            try {
                rtpSendJob?.cancel()
                rtpReceiveJob?.cancel()
                callListenerJob?.cancel()

                aecEffect?.release()
                aecEffect = null
                nsEffect?.release()
                nsEffect = null

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

                rtpSocket?.close()
                rtpSocket = null

                sipSocket?.close()
                sipSocket = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
