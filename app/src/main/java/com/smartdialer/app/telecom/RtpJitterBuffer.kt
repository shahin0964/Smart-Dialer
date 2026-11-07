package com.smartdialer.app.telecom

import kotlin.math.abs

/**
 * Adaptive Sequence-Aware RTP Jitter Buffer with G.711 Packet Loss Concealment (PLC).
 *
 * Key Capabilities:
 * 1. 16-bit sequence number sorting: guarantees packets are played in order even when UDP packets
 *    arrive out-of-order over cellular 4G/5G or Wi-Fi.
 * 2. Duplicate rejection: discards duplicate network retransmissions.
 * 3. G.711 PLC: synthetically reconstructs missing audio frames with energy attenuation decay
 *    instead of harsh clicks, gaps, or silence.
 * 4. Latency capping: bounds jitter buffer depth (max ~160ms) to ensure low latency during active speech.
 * 5. Realtime metrics: calculates packet loss percentage and RFC 3550 interarrival jitter.
 */
class RtpJitterBuffer(
    private val defaultFrameSize: Int = 160 // 20ms @ 8000Hz PCM
) {
    data class Frame(
        val seq: Int,
        val timestamp: Long,
        val pcm: ShortArray,
        val arrivalTimeMs: Long
    )

    private val lock = Any()
    private val buffer = ArrayList<Frame>()
    private var lastPlayedSeq: Int = -1
    private var lastPlayedFrame: ShortArray? = null
    private var consecutivePlcCount = 0

    // Quality Metrics
    @Volatile
    var receivedPackets: Long = 0L
        private set

    @Volatile
    var lostPackets: Long = 0L
        private set

    @Volatile
    var estimatedJitterMs: Double = 0.0
        private set

    private var lastTransitTime: Long = 0L

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            lastPlayedSeq = -1
            lastPlayedFrame = null
            consecutivePlcCount = 0
            receivedPackets = 0L
            lostPackets = 0L
            estimatedJitterMs = 0.0
            lastTransitTime = 0L
        }
    }

    /**
     * Inserts an RTP audio frame in sorted sequence order.
     */
    fun insert(seq: Int, timestamp: Long, pcm: ShortArray, arrivalTimeMs: Long) {
        synchronized(lock) {
            receivedPackets++

            // RFC 3550 Interarrival Jitter calculation (8 samples per ms at 8kHz)
            val currentTransit = arrivalTimeMs - (timestamp / 8)
            if (lastTransitTime != 0L) {
                val d = abs(currentTransit - lastTransitTime)
                estimatedJitterMs += (d - estimatedJitterMs) / 16.0
            }
            lastTransitTime = currentTransit

            // Reject old packets that arrived after we already played past their sequence
            if (lastPlayedSeq != -1) {
                val diffFromLast = seqDiff(seq, lastPlayedSeq)
                if (diffFromLast <= 0) {
                    return // Outdated packet; drop
                }
            }

            // Insert in ascending sequence order
            var insertIndex = 0
            while (insertIndex < buffer.size) {
                val item = buffer[insertIndex]
                val diff = seqDiff(seq, item.seq)
                if (diff == 0) {
                    return // Duplicate packet; drop
                }
                if (diff < 0) {
                    break
                }
                insertIndex++
            }
            buffer.add(insertIndex, Frame(seq, timestamp, pcm, arrivalTimeMs))

            // Keep buffer latency bounded (max 8 frames = 160ms) to prevent delay drift
            while (buffer.size > 8) {
                buffer.removeAt(0)
                lostPackets++
            }
        }
    }

    /**
     * Polls the next frame for playback.
     * If the next expected packet is missing or the buffer underruns, generates a G.711 PLC frame.
     */
    fun pollNextFrame(prebufferThreshold: Int = 3): ShortArray? {
        synchronized(lock) {
            // Initial prebuffering to absorb startup jitter
            if (lastPlayedSeq == -1 && buffer.size < prebufferThreshold) {
                return null
            }

            if (buffer.isEmpty()) {
                // Buffer underrun during call: apply PLC if active speech was playing
                return if (lastPlayedSeq != -1) generatePlcFrame() else null
            }

            val nextExpectedSeq = if (lastPlayedSeq == -1) buffer[0].seq else (lastPlayedSeq + 1) and 0xFFFF
            val head = buffer[0]
            val diff = seqDiff(head.seq, nextExpectedSeq)

            return if (diff <= 0) {
                // Next in sequence or resynced
                buffer.removeAt(0)
                lastPlayedSeq = head.seq
                lastPlayedFrame = head.pcm
                consecutivePlcCount = 0
                head.pcm
            } else {
                // Packet gap detected: nextExpectedSeq was lost in transit
                lostPackets++
                lastPlayedSeq = nextExpectedSeq
                generatePlcFrame()
            }
        }
    }

    /**
     * Generates a Packet Loss Concealment (PLC) frame using smooth energy attenuation.
     */
    private fun generatePlcFrame(): ShortArray {
        consecutivePlcCount++
        val prev = lastPlayedFrame
        val len = prev?.size ?: defaultFrameSize
        val plc = ShortArray(len)
        val decay = when (consecutivePlcCount) {
            1 -> 0.70f // 70% energy
            2 -> 0.35f // 35% energy
            else -> 0.0f // Fade to silence after 40ms loss to avoid robotic tone
        }
        if (prev != null && decay > 0f) {
            for (i in 0 until len) {
                plc[i] = (prev[i] * decay).toInt().toShort()
            }
        }
        lastPlayedFrame = plc
        return plc
    }

    /**
     * Signed 16-bit sequence difference accounting for 0xFFFF wraparound.
     */
    private fun seqDiff(a: Int, b: Int): Int {
        return ((a - b + 32768) and 0xFFFF) - 32768
    }

    fun size(): Int = synchronized(lock) { buffer.size }
}
