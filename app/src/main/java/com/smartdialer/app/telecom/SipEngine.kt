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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.data.repository.IpCallPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * ITU-T Recommendation G.711 Audio Codec (u-Law and A-Law) with precomputed lookup tables.
 * Eliminates bitwise edge-case distortion and provides crystal-clear, zero-latency 8kHz PCM audio.
 */
object G711 {
    val alawToLinearTable = ShortArray(256) { i ->
        val b = i xor 0xD5
        val sign = b and 0x80
        val exp = (b shr 4) and 0x07
        val mant = b and 0x0F
        val sample = if (exp == 0) {
            (mant shl 4) + 8
        } else {
            ((mant shl 4) + 0x108) shl (exp - 1)
        }
        (if (sign != 0) -sample else sample).toShort()
    }

    val ulawToLinearTable = ShortArray(256) { i ->
        val u = i.inv() and 0xFF
        val sign = u and 0x80
        val exp = (u shr 4) and 0x07
        val mant = u and 0x0F
        val sample = (((mant shl 3) + 0x84) shl exp) - 0x84
        (if (sign != 0) -sample else sample).toShort()
    }

    fun alawToLinear(byte: Byte): Short = alawToLinearTable[byte.toInt() and 0xFF]

    fun ulawToLinear(byte: Byte): Short = ulawToLinearTable[byte.toInt() and 0xFF]

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

    fun linearToUlaw(sample: Short): Byte {
        var pcm = sample.toInt()
        val sign = (pcm shr 8) and 0x80
        if (sign != 0) pcm = -pcm
        if (pcm > 32635) pcm = 32635
        pcm += 0x84

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
 * Full-Duplex SIP & RTP Engine (RFC 3261, RFC 2617, RFC 3550, RFC 4566, G.711 PCMA/PCMU).
 * Supports persistent SIP background registration, incoming IP calls, outgoing IP calls,
 * and high-fidelity jitter-buffered HD Voice audio with zero robotic distortion.
 */
object SipEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _registrationStatus = MutableStateFlow("Offline")
    val registrationStatus: StateFlow<String> = _registrationStatus.asStateFlow()

    private val isCallActive = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val isSpeakerOn = AtomicBoolean(false)
    private val isListening = AtomicBoolean(false)

    // Call Context Variables
    private var activeCallId: String? = null
    private var fromTag: String? = null
    private var toTag: String? = null
    private var currentCSeq = 1
    private var currentDestination = ""
    private var currentDomain = ""
    private var contactUri: String? = null

    // Incoming Call Context
    private var incomingCallId: String? = null
    private var incomingVia: String? = null
    private var incomingFrom: String? = null
    private var incomingTo: String? = null
    private var incomingCSeq: String? = null
    private var incomingToTag: String? = null
    private var incomingServerAddr: InetAddress? = null
    private var incomingServerPort: Int = 5060
    private var incomingRemoteSdp: String = ""

    // RTP & Audio Stream Variables
    private var remoteRtpIp: String? = null
    private var remoteRtpPort: Int = 0
    private var negotiatedCodec: Int = 8 // Default to 8 (PCMA - standard in Bangladesh & Asia)
    private var localRtpPort = 40002

    private var sipSocket: DatagramSocket? = null
    private var rtpSocket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var aecEffect: AcousticEchoCanceler? = null
    private var nsEffect: NoiseSuppressor? = null

    // Jitter Buffer for smooth audio playback
    private val jitterQueue = ConcurrentLinkedQueue<ShortArray>()

    private var sipListenerJob: Job? = null
    private var keepAliveJob: Job? = null
    private var rtpSendJob: Job? = null
    private var rtpRecvJob: Job? = null
    private var rtpPlayJob: Job? = null

    private var onCallStateChanged: ((CallStatus, String?) -> Unit)? = null
    private var appContext: Context? = null
    private var engineWakeLock: PowerManager.WakeLock? = null
    private var engineWifiLock: WifiManager.WifiLock? = null

    // Single receiver message stream for dispatching SIP responses and requests
    private val sipMessageFlow = MutableSharedFlow<SipMessage>(
        replay = 16,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun isCallRunning(): Boolean = isCallActive.get()

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

    private fun ensureReceiverRunning(context: Context) {
        appContext = context.applicationContext
        if (sipSocket == null || sipSocket!!.isClosed) {
            try {
                sipSocket = DatagramSocket()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isListening.set(true)
        if (sipListenerJob != null && sipListenerJob?.isActive == true) return

        sipListenerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            while (isActive && isListening.get()) {
                val sock = sipSocket ?: break
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    sock.receive(packet)
                } catch (e: Exception) {
                    continue
                }

                val text = String(packet.data, 0, packet.length)
                val msg = SipMessage.parse(text) ?: continue

                if (!msg.isResponse) {
                    // Incoming SIP Requests
                    when (msg.method) {
                        "INVITE" -> handleIncomingInvite(msg, packet.address, packet.port)
                        "CANCEL" -> handleIncomingCancel(msg, packet.address, packet.port)
                        "BYE" -> handleIncomingBye(msg, packet.address, packet.port)
                        "OPTIONS" -> handleIncomingOptions(msg, packet.address, packet.port)
                    }
                } else {
                    // Forward all SIP responses to awaiting requests
                    sipMessageFlow.emit(msg)
                }
            }
        }
    }

    /**
     * Initializes and starts continuous SIP background listener & registration for Incoming Calls.
     */
    fun startListening(context: Context) {
        appContext = context.applicationContext
        val prefs = IpCallPreferences(context)
        if (!prefs.isConfigured || prefs.username.isBlank() || prefs.domain.isBlank()) {
            _registrationStatus.value = "Offline"
            return
        }

        ensureReceiverRunning(context)

        // Initial Registration
        scope.launch {
            registerInternal(context)
        }

        // Start Periodic NAT Keep-Alive / Re-Registration (every 45s)
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && isListening.get()) {
                delay(45_000)
                if (!isCallActive.get()) {
                    registerInternal(context)
                }
            }
        }
    }

    fun stopListening() {
        isListening.set(false)
        sipListenerJob?.cancel()
        sipListenerJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
        try {
            sipSocket?.close()
            sipSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _registrationStatus.value = "Offline"
    }

    private suspend fun registerInternal(context: Context): Pair<Boolean, String> {
        val prefs = IpCallPreferences(context)
        val user = prefs.username.trim()
        val pass = prefs.password
        val rawDomain = if (prefs.domain.isNotBlank()) prefs.domain.trim() else prefs.outboundProxy.trim()
        val authUser = if (prefs.authName.isNotBlank()) prefs.authName.trim() else user

        if (user.isBlank() || rawDomain.isBlank()) {
            return false to "Please configure Username and SIP Domain"
        }

        ensureReceiverRunning(context)
        val sock = sipSocket ?: return false to "Failed to open network socket"

        return withContext(Dispatchers.IO) {
            try {
                val domain = rawDomain.removePrefix("sip:").removePrefix("sips:").substringBefore(":").trim()
                val serverPort = if (rawDomain.contains(":")) {
                    rawDomain.substringAfterLast(":").toIntOrNull() ?: 5060
                } else 5060

                val localIp = getLocalIp()
                val localPort = sock.localPort
                val callId = UUID.randomUUID().toString() + "@" + localIp
                val tag = UUID.randomUUID().toString().replace("-", "").take(8)
                val targetAddr = InetAddress.getByName(domain)

                // 1. Subscribe for 1st response BEFORE sending initial REGISTER
                val resp1Deferred = scope.async {
                    withTimeoutOrNull(5000) {
                        sipMessageFlow.first { it.isResponse && it.getHeader("call-id") == callId }
                    }
                }

                val branch1 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                val reg1 = buildString {
                    append("REGISTER sip:$domain SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch1;rport\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <sip:$user@$domain>;tag=$tag\r\n")
                    append("To: <sip:$user@$domain>\r\n")
                    append("Call-ID: $callId\r\n")
                    append("CSeq: 1 REGISTER\r\n")
                    append("Contact: <sip:$user@$localIp:$localPort;transport=udp>\r\n")
                    append("Expires: 300\r\n")
                    append("User-Agent: SmartDialer/1.0\r\n")
                    append("Content-Length: 0\r\n\r\n")
                }

                sock.send(DatagramPacket(reg1.toByteArray(), reg1.length, targetAddr, serverPort))

                val msg1 = resp1Deferred.await()
                if (msg1 == null) {
                    _registrationStatus.value = "Offline (Timeout)"
                    return@withContext false to "Server Connection Timeout (No response from $domain)"
                }

                if (msg1.statusCode == 200) {
                    _registrationStatus.value = "Online / Registered"
                    return@withContext true to "SIP Account Connected & Registered!"
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

                    // 2. Subscribe for 2nd response BEFORE sending authenticated REGISTER
                    val resp2Deferred = scope.async {
                        withTimeoutOrNull(5000) {
                            sipMessageFlow.first { it.isResponse && it.getHeader("call-id") == callId && it !== msg1 }
                        }
                    }

                    val branch2 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                    val reg2 = buildString {
                        append("REGISTER sip:$domain SIP/2.0\r\n")
                        append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch2;rport\r\n")
                        append("Max-Forwards: 70\r\n")
                        append("From: <sip:$user@$domain>;tag=$tag\r\n")
                        append("To: <sip:$user@$domain>\r\n")
                        append("Call-ID: $callId\r\n")
                        append("CSeq: 2 REGISTER\r\n")
                        append("Contact: <sip:$user@$localIp:$localPort;transport=udp>\r\n")
                        append("$authHeaderName: $authValue\r\n")
                        append("Expires: 300\r\n")
                        append("User-Agent: SmartDialer/1.0\r\n")
                        append("Content-Length: 0\r\n\r\n")
                    }

                    sock.send(DatagramPacket(reg2.toByteArray(), reg2.length, targetAddr, serverPort))

                    val msg2 = resp2Deferred.await()
                    if (msg2?.statusCode == 200) {
                        _registrationStatus.value = "Online / Registered"
                        true to "SIP Account Connected & Registered!"
                    } else if (msg2 != null) {
                        _registrationStatus.value = "Auth Failed (${msg2.statusCode})"
                        false to "Authentication Failed: ${msg2.statusCode} ${msg2.statusReason}"
                    } else {
                        _registrationStatus.value = "Offline (Timeout)"
                        false to "Authentication Timeout"
                    }
                } else {
                    _registrationStatus.value = "Offline (${msg1.statusCode})"
                    false to "Server Error: ${msg1.statusCode} ${msg1.statusReason}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _registrationStatus.value = "Offline"
                false to "Connection Error: ${e.localizedMessage ?: "Network error"}"
            }
        }
    }

    /**
     * Handles incoming SIP INVITE from server (Someone calling our IP number).
     */
    private fun handleIncomingInvite(msg: SipMessage, senderAddr: InetAddress, senderPort: Int) {
        val fromHeader = msg.getHeader("from") ?: ""
        // Extract caller number: <sip:01712345678@... or sip:123@...
        val numberMatch = Regex("""sip:([^@;>]+)""").find(fromHeader)
        val callerNumber = numberMatch?.groupValues?.get(1) ?: "Unknown"

        // Extract caller display name if present: "Customer Care" <sip:...
        val nameMatch = Regex(""""([^"]+)"""").find(fromHeader)
        val callerName = nameMatch?.groupValues?.get(1)

        val localIp = getLocalIp()
        val localPort = sipSocket?.localPort ?: 5060
        val toHeader = msg.getHeader("to") ?: ""
        incomingToTag = UUID.randomUUID().toString().replace("-", "").take(8)

        incomingCallId = msg.getHeader("call-id")
        incomingVia = msg.getHeader("via")
        incomingFrom = fromHeader
        incomingTo = toHeader
        incomingCSeq = msg.getHeader("cseq")
        incomingServerAddr = senderAddr
        incomingServerPort = senderPort
        incomingRemoteSdp = msg.body

        // 1. Send 180 Ringing immediately
        val ringingMsg = buildString {
            append("SIP/2.0 180 Ringing\r\n")
            incomingVia?.let { append("Via: $it\r\n") }
            incomingFrom?.let { append("From: $it\r\n") }
            append("To: ${incomingTo};tag=$incomingToTag\r\n")
            incomingCallId?.let { append("Call-ID: $it\r\n") }
            incomingCSeq?.let { append("CSeq: $it\r\n") }
            append("Contact: <sip:user@$localIp:$localPort;transport=udp>\r\n")
            append("User-Agent: SmartDialer/1.0\r\n")
            append("Content-Length: 0\r\n\r\n")
        }
        try {
            val packet = DatagramPacket(ringingMsg.toByteArray(), ringingMsg.length, senderAddr, senderPort)
            sipSocket?.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Notify CallManager to show Incoming Ringing UI & Heads-Up Notification
        appContext?.let { ctx ->
            CallManager.handleIncomingIpCall(ctx, callerNumber, callerName)
        }
    }

    /**
     * User accepted incoming call (tapped Answer).
     */
    fun answerIncomingCall() {
        scope.launch {
            val ctx = appContext ?: return@launch
            val serverAddr = incomingServerAddr ?: return@launch
            val serverPort = incomingServerPort

            isCallActive.set(true)
            isMuted.set(false)
            isSpeakerOn.set(false)

            val localIp = getLocalIp()
            val localSipPort = sipSocket?.localPort ?: 5060

            // Allocate RTP socket
            localRtpPort = (40000..50000).random()
            if (localRtpPort % 2 != 0) localRtpPort++
            rtpSocket = DatagramSocket(localRtpPort)
            rtpSocket?.soTimeout = 1000

            extractRemoteSdp(incomingRemoteSdp, serverAddr.hostAddress ?: "103.170.231.10")

            // Build 200 OK with SDP Answer (PCMA 8, PCMU 0)
            val sdpAnswer = buildString {
                append("v=0\r\n")
                append("o=- ${System.currentTimeMillis()} 1 IN IP4 $localIp\r\n")
                append("s=SmartDialer\r\n")
                append("c=IN IP4 $localIp\r\n")
                append("t=0 0\r\n")
                append("m=audio $localRtpPort RTP/AVP $negotiatedCodec 101\r\n")
                if (negotiatedCodec == 8) {
                    append("a=rtpmap:8 PCMA/8000\r\n")
                    append("a=rtpmap:0 PCMU/8000\r\n")
                } else {
                    append("a=rtpmap:0 PCMU/8000\r\n")
                    append("a=rtpmap:8 PCMA/8000\r\n")
                }
                append("a=rtpmap:101 telephone-event/8000\r\n")
                append("a=fmtp:101 0-16\r\n")
                append("a=sendrecv\r\n")
            }

            val ok200 = buildString {
                append("SIP/2.0 200 OK\r\n")
                incomingVia?.let { append("Via: $it\r\n") }
                incomingFrom?.let { append("From: $it\r\n") }
                append("To: ${incomingTo};tag=$incomingToTag\r\n")
                incomingCallId?.let { append("Call-ID: $it\r\n") }
                incomingCSeq?.let { append("CSeq: $it\r\n") }
                append("Contact: <sip:user@$localIp:$localSipPort;transport=udp>\r\n")
                append("User-Agent: SmartDialer/1.0\r\n")
                append("Content-Type: application/sdp\r\n")
                append("Content-Length: ${sdpAnswer.length}\r\n\r\n")
                append(sdpAnswer)
            }

            try {
                val packet = DatagramPacket(ok200.toByteArray(), ok200.length, serverAddr, serverPort)
                sipSocket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Configure audio for active communication
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = false

            startRtpAudio(ctx)
        }
    }

    /**
     * User declined incoming call (tapped Decline).
     */
    fun rejectIncomingCall() {
        scope.launch {
            val serverAddr = incomingServerAddr ?: return@launch
            val serverPort = incomingServerPort

            val rejectMsg = buildString {
                append("SIP/2.0 486 Busy Here\r\n")
                incomingVia?.let { append("Via: $it\r\n") }
                incomingFrom?.let { append("From: $it\r\n") }
                append("To: ${incomingTo};tag=$incomingToTag\r\n")
                incomingCallId?.let { append("Call-ID: $it\r\n") }
                incomingCSeq?.let { append("CSeq: $it\r\n") }
                append("User-Agent: SmartDialer/1.0\r\n")
                append("Content-Length: 0\r\n\r\n")
            }
            try {
                val packet = DatagramPacket(rejectMsg.toByteArray(), rejectMsg.length, serverAddr, serverPort)
                sipSocket?.send(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Remote caller cancelled the incoming call before user answered.
     */
    private fun handleIncomingCancel(msg: SipMessage, senderAddr: InetAddress, senderPort: Int) {
        val okMsg = buildString {
            append("SIP/2.0 200 OK\r\n")
            msg.getHeader("via")?.let { append("Via: $it\r\n") }
            msg.getHeader("from")?.let { append("From: $it\r\n") }
            msg.getHeader("to")?.let { append("To: $it\r\n") }
            msg.getHeader("call-id")?.let { append("Call-ID: $it\r\n") }
            msg.getHeader("cseq")?.let { append("CSeq: $it\r\n") }
            append("Content-Length: 0\r\n\r\n")
        }
        val termMsg = buildString {
            append("SIP/2.0 487 Request Terminated\r\n")
            incomingVia?.let { append("Via: $it\r\n") }
            incomingFrom?.let { append("From: $it\r\n") }
            append("To: ${incomingTo};tag=$incomingToTag\r\n")
            incomingCallId?.let { append("Call-ID: $it\r\n") }
            incomingCSeq?.let { append("CSeq: $it\r\n") }
            append("Content-Length: 0\r\n\r\n")
        }
        try {
            sipSocket?.send(DatagramPacket(okMsg.toByteArray(), okMsg.length, senderAddr, senderPort))
            sipSocket?.send(DatagramPacket(termMsg.toByteArray(), termMsg.length, senderAddr, senderPort))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        CallManager.handleIncomingIpCallCancelled()
    }

    /**
     * Remote party ended active call.
     */
    private fun handleIncomingBye(msg: SipMessage, senderAddr: InetAddress, senderPort: Int) {
        val resp = buildString {
            append("SIP/2.0 200 OK\r\n")
            msg.getHeader("via")?.let { append("Via: $it\r\n") }
            msg.getHeader("from")?.let { append("From: $it\r\n") }
            msg.getHeader("to")?.let { append("To: $it\r\n") }
            msg.getHeader("call-id")?.let { append("Call-ID: $it\r\n") }
            msg.getHeader("cseq")?.let { append("CSeq: $it\r\n") }
            append("Content-Length: 0\r\n\r\n")
        }
        try {
            sipSocket?.send(DatagramPacket(resp.toByteArray(), resp.length, senderAddr, senderPort))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        endCall()
        CallManager.disconnect()
    }

    private fun handleIncomingOptions(msg: SipMessage, senderAddr: InetAddress, senderPort: Int) {
        val resp = buildString {
            append("SIP/2.0 200 OK\r\n")
            msg.getHeader("via")?.let { append("Via: $it\r\n") }
            msg.getHeader("from")?.let { append("From: $it\r\n") }
            msg.getHeader("to")?.let { append("To: $it\r\n") }
            msg.getHeader("call-id")?.let { append("Call-ID: $it\r\n") }
            msg.getHeader("cseq")?.let { append("CSeq: $it\r\n") }
            append("Allow: INVITE, ACK, CANCEL, BYE, NOTIFY, REFER, MESSAGE, OPTIONS, INFO\r\n")
            append("Content-Length: 0\r\n\r\n")
        }
        try {
            sipSocket?.send(DatagramPacket(resp.toByteArray(), resp.length, senderAddr, senderPort))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Initiates an outgoing IP/SIP Call to destination number.
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
        currentDestination = destination.trim().replace(" ", "").replace("-", "")

        val prefs = IpCallPreferences(context)
        val user = prefs.username.trim()
        val pass = prefs.password
        val domain = if (prefs.domain.isNotBlank()) prefs.domain.trim() else prefs.outboundProxy.trim()
        val authUser = if (prefs.authName.isNotBlank()) prefs.authName.trim() else user
        currentDomain = domain

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

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = false

        onCallStateChanged?.invoke(CallStatus.CONNECTING, "Connecting IP Call...")

        scope.launch {
            try {
                ensureReceiverRunning(context)
                val sock = sipSocket ?: throw IllegalStateException("Network socket not ready")

                localRtpPort = (40000..50000).random()
                if (localRtpPort % 2 != 0) localRtpPort++
                rtpSocket = DatagramSocket(localRtpPort)
                rtpSocket?.soTimeout = 1000

                val cleanDomain = domain.removePrefix("sip:").removePrefix("sips:").substringBefore(":").trim()
                val serverPort = if (domain.contains(":")) {
                    domain.substringAfterLast(":").toIntOrNull() ?: 5060
                } else 5060

                val localIp = getLocalIp()
                val localSipPort = sock.localPort
                val serverAddr = InetAddress.getByName(cleanDomain)

                // SDP Offer with preferred codecs (PCMA 8 first, PCMU 0 second)
                val sdpOffer = buildString {
                    append("v=0\r\n")
                    append("o=- ${System.currentTimeMillis()} 1 IN IP4 $localIp\r\n")
                    append("s=SmartDialer\r\n")
                    append("c=IN IP4 $localIp\r\n")
                    append("t=0 0\r\n")
                    append("m=audio $localRtpPort RTP/AVP 8 0 101\r\n")
                    append("a=rtpmap:8 PCMA/8000\r\n")
                    append("a=rtpmap:0 PCMU/8000\r\n")
                    append("a=rtpmap:101 telephone-event/8000\r\n")
                    append("a=fmtp:101 0-16\r\n")
                    append("a=sendrecv\r\n")
                }

                // 1. Send initial INVITE
                val branch1 = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                val invite1 = buildString {
                    append("INVITE sip:$currentDestination@$cleanDomain SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$branch1;rport\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <sip:$user@$cleanDomain>;tag=$fromTag\r\n")
                    append("To: <sip:$currentDestination@$cleanDomain>\r\n")
                    append("Call-ID: $activeCallId\r\n")
                    append("CSeq: 1 INVITE\r\n")
                    append("Contact: <sip:$user@$localIp:$localSipPort;transport=udp>\r\n")
                    append("User-Agent: SmartDialer/1.0\r\n")
                    append("Content-Type: application/sdp\r\n")
                    append("Content-Length: ${sdpOffer.length}\r\n\r\n")
                    append(sdpOffer)
                }

                sock.send(DatagramPacket(invite1.toByteArray(), invite1.length, serverAddr, serverPort))

                var callEstablished = false

                withTimeoutOrNull(45_000) {
                    sipMessageFlow.takeWhile { !callEstablished && isCallActive.get() && isActive }.collect { msg ->
                        if (msg.isResponse && msg.getHeader("call-id") == activeCallId) {
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
                                    if (msg.body.contains("m=audio")) {
                                        extractRemoteSdp(msg.body, cleanDomain)
                                        startRtpAudio(context)
                                    }
                                }
                                401, 407 -> {
                                    // Send ACK for challenge
                                    val ackMsg = buildString {
                                        append("ACK sip:$currentDestination@$cleanDomain SIP/2.0\r\n")
                                        append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$branch1;rport\r\n")
                                        append("Max-Forwards: 70\r\n")
                                        append("From: <sip:$user@$cleanDomain>;tag=$fromTag\r\n")
                                        append("To: <sip:$currentDestination@$cleanDomain>\r\n")
                                        append("Call-ID: $activeCallId\r\n")
                                        append("CSeq: 1 ACK\r\n")
                                        append("Content-Length: 0\r\n\r\n")
                                    }
                                    sock.send(DatagramPacket(ackMsg.toByteArray(), ackMsg.length, serverAddr, serverPort))

                                    val authHeader = msg.getHeader("www-authenticate") ?: msg.getHeader("proxy-authenticate") ?: ""
                                    val challenge = parseAuthChallenge(authHeader)
                                    val realm = challenge["realm"] ?: cleanDomain
                                    val nonce = challenge["nonce"] ?: ""
                                    val qop = challenge["qop"]
                                    val nc = "00000001"
                                    val cnonce = UUID.randomUUID().toString().replace("-", "").take(16)
                                    val uriTarget = "sip:$currentDestination@$cleanDomain"
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
                                        append("INVITE sip:$currentDestination@$cleanDomain SIP/2.0\r\n")
                                        append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$branch2;rport\r\n")
                                        append("Max-Forwards: 70\r\n")
                                        append("From: <sip:$user@$cleanDomain>;tag=$fromTag\r\n")
                                        append("To: <sip:$currentDestination@$cleanDomain>\r\n")
                                        append("Call-ID: $activeCallId\r\n")
                                        append("CSeq: 2 INVITE\r\n")
                                        append("Contact: <sip:$user@$localIp:$localSipPort;transport=udp>\r\n")
                                        append("$authHeaderName: $authValue\r\n")
                                        append("User-Agent: SmartDialer/1.0\r\n")
                                        append("Content-Type: application/sdp\r\n")
                                        append("Content-Length: ${sdpOffer.length}\r\n\r\n")
                                        append(sdpOffer)
                                    }

                                    sock.send(DatagramPacket(invite2.toByteArray(), invite2.length, serverAddr, serverPort))
                                }
                                200 -> {
                                    callEstablished = true
                                    val toHeader = msg.getHeader("to") ?: ""
                                    val tagMatch = Regex("""tag=([^;,\s]+)""").find(toHeader)
                                    toTag = tagMatch?.groupValues?.get(1)

                                    contactUri = msg.getHeader("contact")?.let { raw ->
                                        val match = Regex("""<([^>]+)>""").find(raw)
                                        match?.groupValues?.get(1) ?: raw
                                    }

                                    extractRemoteSdp(msg.body, cleanDomain)

                                    // Send ACK for 200 OK
                                    val ackBranch = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                                    val ackTarget = contactUri ?: "sip:$currentDestination@$cleanDomain"
                                    val ackMsg = buildString {
                                        append("ACK $ackTarget SIP/2.0\r\n")
                                        append("Via: SIP/2.0/UDP $localIp:$localSipPort;branch=$ackBranch;rport\r\n")
                                        append("Max-Forwards: 70\r\n")
                                        append("From: <sip:$user@$cleanDomain>;tag=$fromTag\r\n")
                                        append("To: <sip:$currentDestination@$cleanDomain>${if (toTag != null) ";tag=$toTag" else ""}\r\n")
                                        append("Call-ID: $activeCallId\r\n")
                                        append("CSeq: $currentCSeq ACK\r\n")
                                        append("Contact: <sip:$user@$localIp:$localSipPort;transport=udp>\r\n")
                                        append("Content-Length: 0\r\n\r\n")
                                    }
                                    sock.send(DatagramPacket(ackMsg.toByteArray(), ackMsg.length, serverAddr, serverPort))

                                    withContext(Dispatchers.Main) {
                                        onCallStateChanged?.invoke(CallStatus.ACTIVE, null)
                                    }

                                    startRtpAudio(context)
                                }
                                486 -> {
                                    withContext(Dispatchers.Main) {
                                        onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "Busy")
                                    }
                                    endCall()
                                }
                                404 -> {
                                    withContext(Dispatchers.Main) {
                                        onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "Number not found")
                                    }
                                    endCall()
                                }
                                in 400..699 -> {
                                    withContext(Dispatchers.Main) {
                                        onCallStateChanged?.invoke(CallStatus.DISCONNECTED, msg.statusReason)
                                    }
                                    endCall()
                                }
                            }
                        }
                    }
                }

                if (!callEstablished && isCallActive.get()) {
                    withContext(Dispatchers.Main) {
                        onCallStateChanged?.invoke(CallStatus.DISCONNECTED, "Call Timeout / No Answer")
                    }
                    endCall()
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
        val ipMatch = Regex("""c=IN IP4\s+([0-9.]+)""").find(sdp)
        remoteRtpIp = ipMatch?.groupValues?.get(1) ?: fallbackHost

        val audioMatch = Regex("""m=audio\s+(\d+)\s+RTP/AVP\s+([0-9\s]+)""").find(sdp)
        if (audioMatch != null) {
            remoteRtpPort = audioMatch.groupValues[1].toIntOrNull() ?: 16428
            val codecs = audioMatch.groupValues[2].trim().split(Regex("""\s+"""))
            // Pick first supported codec: prefer PCMA 8 if offered, else PCMU 0
            val chosen = codecs.firstOrNull { it == "8" || it == "0" }
            negotiatedCodec = chosen?.toIntOrNull() ?: 8
        }
    }

    /**
     * High-Fidelity Jitter-Buffered Bidirectional RTP Audio Engine.
     * Guarantees smooth continuous voice playback without robotic distortion or buffer underrun.
     */
    private fun startRtpAudio(context: Context) {
        if (rtpSendJob != null && rtpSendJob?.isActive == true) return

        try {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (engineWakeLock == null) {
                engineWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartDialer:SipEngineWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (engineWifiLock == null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                engineWifiLock = wm?.createWifiLock(mode, "SmartDialer:SipEngineWifiLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sampleRate = 8000
        val pcmFrameSize = 160 // 20ms at 8kHz

        jitterQueue.clear()

        // 1. AudioRecord (Hardware AEC & NS)
        val minRecBuf = max(
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2,
            2560
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

        // 2. AudioTrack with expanded buffer (prevents underruns)
        val minTrackBuf = max(
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4,
            8192
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

        // 3. RTP Transmission Loop (Mic -> PCMA/PCMU -> Remote)
        rtpSendJob = scope.launch(Dispatchers.IO) {
            val pcmBuf = ShortArray(pcmFrameSize)
            val rtpPacketData = ByteArray(12 + pcmFrameSize)
            var seq = 0
            var timestamp = 0L
            val ssrc = (100000..999999).random().toLong()

            rtpPacketData[0] = 0x80.toByte() // V=2
            rtpPacketData[1] = (negotiatedCodec and 0x7F).toByte()
            rtpPacketData[8] = ((ssrc shr 24) and 0xFF).toByte()
            rtpPacketData[9] = ((ssrc shr 16) and 0xFF).toByte()
            rtpPacketData[10] = ((ssrc shr 8) and 0xFF).toByte()
            rtpPacketData[11] = (ssrc and 0xFF).toByte()

            while (isActive && isCallActive.get()) {
                val rec = audioRecord ?: break
                val readCount = rec.read(pcmBuf, 0, pcmFrameSize)
                if (readCount <= 0) continue

                if (isMuted.get()) {
                    for (i in 0 until readCount) pcmBuf[i] = 0
                }

                if (negotiatedCodec == 8) {
                    for (i in 0 until readCount) {
                        rtpPacketData[12 + i] = G711.linearToAlaw(pcmBuf[i])
                    }
                } else {
                    for (i in 0 until readCount) {
                        rtpPacketData[12 + i] = G711.linearToUlaw(pcmBuf[i])
                    }
                }

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
                        // ignore network drop
                    }
                }
            }
        }

        // 4. Dedicated RTP Network Reception Loop -> Enqueues into Jitter Buffer
        rtpRecvJob = scope.launch(Dispatchers.IO) {
            val recvBuffer = ByteArray(1500)

            while (isActive && isCallActive.get()) {
                val sock = rtpSocket ?: break
                val packet = DatagramPacket(recvBuffer, recvBuffer.size)
                try {
                    sock.receive(packet)
                } catch (e: Exception) {
                    continue
                }

                if (packet.length < 12) continue

                // Automatic NAT Symmetric RTP adaptation
                if (remoteRtpIp == null || remoteRtpPort == 0) {
                    remoteRtpIp = packet.address.hostAddress
                    remoteRtpPort = packet.port
                }

                // Check payload type directly from packet byte 1
                val pt = recvBuffer[1].toInt() and 0x7F
                if (pt == 101) {
                    // RFC 2833 DTMF telephone-event: Do NOT decode as audio!
                    continue
                }

                val isAlaw = (pt == 8) || (pt != 0 && negotiatedCodec == 8)
                val payloadLen = packet.length - 12
                if (payloadLen <= 0) continue

                // Decode ALL samples in packet (supports 20ms, 30ms, 40ms packets without dropping)
                val decodedPcm = ShortArray(payloadLen)
                if (isAlaw) {
                    for (i in 0 until payloadLen) {
                        decodedPcm[i] = G711.alawToLinear(recvBuffer[12 + i])
                    }
                } else {
                    for (i in 0 until payloadLen) {
                        decodedPcm[i] = G711.ulawToLinear(recvBuffer[12 + i])
                    }
                }

                // Prevent excessive buffer delay (cap at 120ms max jitter backlog)
                while (jitterQueue.size > 6) {
                    jitterQueue.poll()
                }
                jitterQueue.offer(decodedPcm)
            }
        }

        // 5. Dedicated Smooth AudioTrack Playback Loop
        rtpPlayJob = scope.launch(Dispatchers.IO) {
            // Pre-buffer 40ms (2 packets) before playing for rock-solid stability
            while (isActive && isCallActive.get() && jitterQueue.size < 2) {
                delay(10)
            }

            val comfortSilence = ShortArray(160)

            while (isActive && isCallActive.get()) {
                val track = audioTrack ?: break
                val frame = jitterQueue.poll()
                if (frame != null) {
                    track.write(frame, 0, frame.size, AudioTrack.WRITE_BLOCKING)
                } else {
                    // Buffer underrun protection: play comfort silence so AudioTrack never glitches
                    track.write(comfortSilence, 0, comfortSilence.size, AudioTrack.WRITE_BLOCKING)
                    delay(10)
                }
            }
        }
    }

    /**
     * Sends DTMF digit over SIP (INFO application/dtmf-relay).
     */
    fun sendDtmf(digit: Char) {
        scope.launch {
            val domain = currentDomain
            val callId = activeCallId ?: incomingCallId
            val from = fromTag ?: "smartdialer"
            val target = currentDestination
            if (domain.isBlank() || callId.isNullOrBlank()) return@launch

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
     * Tests SIP Registration manually (from Settings Screen).
     */
    fun registerAccount(context: Context, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            withContext(Dispatchers.Main) {
                _registrationStatus.value = "Connecting..."
            }
            val (success, msg) = registerInternal(context)
            withContext(Dispatchers.Main) {
                onResult(success, msg)
            }
        }
    }

    /**
     * Ends active or dialing call. Sends BYE or CANCEL.
     */
    fun endCall() {
        if (!isCallActive.getAndSet(false)) return

        scope.launch {
            val domain = currentDomain
            val callId = activeCallId
            val from = fromTag
            val target = currentDestination

            if (domain.isNotBlank() && !callId.isNullOrBlank() && !from.isNullOrBlank()) {
                val cleanDomain = domain.removePrefix("sip:").removePrefix("sips:").substringBefore(":").trim()
                val serverPort = if (domain.contains(":")) {
                    domain.substringAfterLast(":").toIntOrNull() ?: 5060
                } else 5060
                val localIp = getLocalIp()
                val localPort = sipSocket?.localPort ?: 5060
                val serverAddr = try { InetAddress.getByName(cleanDomain) } catch (e: Exception) { null }

                if (serverAddr != null) {
                    val branch = "z9hG4bK-" + UUID.randomUUID().toString().replace("-", "").take(8)
                    if (toTag != null) {
                        val byeTarget = contactUri ?: "sip:$target@$cleanDomain"
                        val byeMsg = buildString {
                            append("BYE $byeTarget SIP/2.0\r\n")
                            append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch;rport\r\n")
                            append("Max-Forwards: 70\r\n")
                            append("From: <sip:user@$cleanDomain>;tag=$from\r\n")
                            append("To: <sip:$target@$cleanDomain>;tag=$toTag\r\n")
                            append("Call-ID: $callId\r\n")
                            append("CSeq: ${++currentCSeq} BYE\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        }
                        try {
                            sipSocket?.send(DatagramPacket(byeMsg.toByteArray(), byeMsg.length, serverAddr, serverPort))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        val cancelMsg = buildString {
                            append("CANCEL sip:$target@$cleanDomain SIP/2.0\r\n")
                            append("Via: SIP/2.0/UDP $localIp:$localPort;branch=$branch;rport\r\n")
                            append("Max-Forwards: 70\r\n")
                            append("From: <sip:user@$cleanDomain>;tag=$from\r\n")
                            append("To: <sip:$target@$cleanDomain>\r\n")
                            append("Call-ID: $callId\r\n")
                            append("CSeq: 1 CANCEL\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        }
                        try {
                            sipSocket?.send(DatagramPacket(cancelMsg.toByteArray(), cancelMsg.length, serverAddr, serverPort))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Teardown Audio Streams (Keep sipSocket alive for incoming calls!)
            try {
                rtpSendJob?.cancel()
                rtpRecvJob?.cancel()
                rtpPlayJob?.cancel()
                jitterQueue.clear()

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

                engineWakeLock?.let { if (it.isHeld) it.release() }
                engineWakeLock = null

                engineWifiLock?.let { if (it.isHeld) it.release() }
                engineWifiLock = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
