package com.smartdialer.app.telecom

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.smartdialer.app.util.AppAnalysisLogger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class CallRecordingItem(
    val file: File,
    val fileName: String,
    val displayTitle: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val timestampMillis: Long,
    val isIpCall: Boolean
) {
    val durationFormatted: String
        get() {
            val totalSec = (durationMillis / 1000L).coerceAtLeast(0L)
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format(Locale.US, "%02d:%02d", min, sec)
        }

    val sizeFormatted: String
        get() {
            val kb = sizeBytes / 1024.0
            return if (kb >= 1024.0) {
                String.format(Locale.US, "%.1f MB", kb / 1024.0)
            } else {
                String.format(Locale.US, "%.0f KB", kb)
            }
        }
}

/**
 * Handles real two-way call recording:
 * - For IP Calls (SipEngine): mixes real-time 8kHz local mic + remote RTP PCM into a standard WAV file.
 * - For Cellular Calls: attempts best-effort MediaRecorder capture (AAC/M4A or AMR).
 */
object CallRecorderManager {

    private val isRecordingFlag = AtomicBoolean(false)
    private var isCurrentIpRecording = false

    // IP Call WAV state
    private var wavFile: File? = null
    private var wavOut: BufferedOutputStream? = null
    private val dataBytesWritten = AtomicLong(0L)
    private val remoteAudioQueue = ConcurrentLinkedQueue<ShortArray>()
    private val wavWriteLock = Any()

    // Cellular MediaRecorder state
    private var mediaRecorder: MediaRecorder? = null
    private var cellularFile: File? = null

    fun isRecording(): Boolean = isRecordingFlag.get()

    fun getRecordingsDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val dir = File(base, "SmartDialerRecordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun startRecording(
        context: Context,
        phoneNumber: String,
        contactName: String?,
        isIpCall: Boolean,
        audioFormatPref: String = "AAC / M4A"
    ): Boolean {
        if (isRecordingFlag.get()) return true

        val dir = getRecordingsDir(context)
        val safeName = (contactName?.takeIf { it.isNotBlank() } ?: phoneNumber.ifBlank { "Unknown" })
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(28)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        return if (isIpCall) {
            startIpWavRecording(dir, safeName, stamp)
        } else {
            startCellularRecording(context, dir, safeName, stamp, audioFormatPref)
        }
    }

    private fun startIpWavRecording(dir: File, safeName: String, stamp: String): Boolean {
        return try {
            val outFile = File(dir, "IP_${safeName}_${stamp}.wav")
            val fos = FileOutputStream(outFile)
            val bos = BufferedOutputStream(fos, 8192)
            // Write 44-byte placeholder WAV header (8000 Hz, 16-bit, Mono)
            bos.write(buildWavHeader(0L, sampleRate = 8000, channels = 1, bitsPerSample = 16))

            synchronized(wavWriteLock) {
                wavFile = outFile
                wavOut = bos
                dataBytesWritten.set(0L)
                remoteAudioQueue.clear()
                isCurrentIpRecording = true
                isRecordingFlag.set(true)
            }
            AppAnalysisLogger.telecom("Recorder", "Started IP two-way WAV recording: ${outFile.name}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            AppAnalysisLogger.error("Recorder", "Failed to start IP WAV recording", e)
            false
        }
    }

    /**
     * Called by SipEngine RTP receive/playback path whenever a decoded remote PCM frame arrives.
     */
    fun onRemotePcmFrame(samples: ShortArray) {
        if (!isRecordingFlag.get() || !isCurrentIpRecording) return
        if (remoteAudioQueue.size > 40) {
            remoteAudioQueue.poll()
        }
        remoteAudioQueue.offer(samples.copyOf())
    }

    /**
     * Called by SipEngine RTP transmit path every ~20ms with the captured local mic PCM frame.
     * Mixes the local mic sample with the corresponding remote sample and writes PCM16LE to disk.
     */
    fun onLocalPcmFrame(localSamples: ShortArray) {
        if (!isRecordingFlag.get() || !isCurrentIpRecording) return
        val remote = remoteAudioQueue.poll()

        synchronized(wavWriteLock) {
            val out = wavOut ?: return
            try {
                val len = localSamples.size
                val byteBuf = ByteArray(len * 2)
                for (i in 0 until len) {
                    val l = localSamples[i].toInt()
                    val r = if (remote != null && i < remote.size) remote[i].toInt() else 0
                    val mixed = (l + r).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    byteBuf[i * 2] = (mixed and 0xFF).toByte()
                    byteBuf[i * 2 + 1] = ((mixed shr 8) and 0xFF).toByte()
                }
                out.write(byteBuf)
                dataBytesWritten.addAndGet(byteBuf.size.toLong())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startCellularRecording(
        context: Context,
        dir: File,
        safeName: String,
        stamp: String,
        audioFormatPref: String
    ): Boolean {
        val useAmr = audioFormatPref.contains("AMR", ignoreCase = true)
        val ext = if (useAmr) "amr" else "m4a"
        val outFile = File(dir, "SIM_${safeName}_${stamp}.$ext")

        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        for (source in sources) {
            var recorder: MediaRecorder? = null
            try {
                recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                recorder.setAudioSource(source)
                if (useAmr) {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                } else {
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioSamplingRate(16000)
                    recorder.setAudioEncodingBitRate(64000)
                }
                recorder.setOutputFile(outFile.absolutePath)
                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                cellularFile = outFile
                isCurrentIpRecording = false
                isRecordingFlag.set(true)
                AppAnalysisLogger.telecom("Recorder", "Started cellular recording via source=$source: ${outFile.name}")
                return true
            } catch (e: Exception) {
                try {
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }

        if (outFile.exists()) outFile.delete()
        Toast.makeText(context, "Cellular call recording blocked by OS/device", Toast.LENGTH_SHORT).show()
        return false
    }

    fun stopRecording(): File? {
        if (!isRecordingFlag.getAndSet(false)) return null

        if (isCurrentIpRecording) {
            isCurrentIpRecording = false
            var finalized: File? = null
            synchronized(wavWriteLock) {
                try {
                    wavOut?.flush()
                    wavOut?.close()
                } catch (_: Exception) {
                }
                wavOut = null
                remoteAudioQueue.clear()

                val f = wavFile
                wavFile = null
                val totalData = dataBytesWritten.get()
                if (f != null && f.exists()) {
                    if (totalData > 0L) {
                        try {
                            RandomAccessFile(f, "rw").use { raf ->
                                raf.seek(0)
                                raf.write(buildWavHeader(totalData, 8000, 1, 16))
                            }
                            finalized = f
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        f.delete()
                    }
                }
            }
            return finalized
        } else {
            val f = cellularFile
            cellularFile = null
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
            mediaRecorder = null
            return if (f != null && f.exists() && f.length() > 512) f else {
                f?.delete()
                null
            }
        }
    }

    fun getRecordings(context: Context): List<CallRecordingItem> {
        val dir = getRecordingsDir(context)
        val files = dir.listFiles { file ->
            file.isFile && (file.extension.equals("wav", true) ||
                file.extension.equals("m4a", true) ||
                file.extension.equals("amr", true))
        } ?: return emptyList()

        return files.sortedByDescending { it.lastModified() }.map { file ->
            val durationMs = extractDurationMillis(file)
            val isIp = file.name.startsWith("IP_")
            val cleanTitle = file.nameWithoutExtension
                .removePrefix("IP_")
                .removePrefix("SIM_")
                .replace(Regex("_\\d{8}_\\d{6}$"), "")
                .replace("_", " ")
                .ifBlank { file.nameWithoutExtension }

            CallRecordingItem(
                file = file,
                fileName = file.name,
                displayTitle = cleanTitle,
                durationMillis = durationMs,
                sizeBytes = file.length(),
                timestampMillis = file.lastModified(),
                isIpCall = isIp
            )
        }
    }

    private fun extractDurationMillis(file: File): Long {
        if (file.extension.equals("wav", true)) {
            val pcmBytes = (file.length() - 44L).coerceAtLeast(0L)
            // 8000 Hz * 2 bytes/sample = 16000 bytes/sec
            return (pcmBytes * 1000L) / 16000L
        }
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun shareRecording(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val mime = when (file.extension.lowercase(Locale.US)) {
                "wav" -> "audio/wav"
                "amr" -> "audio/amr"
                else -> "audio/mp4"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Call Recording").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildWavHeader(
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = totalAudioLen + 36L
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalDataLen.toInt())
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16) // PCM chunk size
        writeShortLE(header, 20, 1) // AudioFormat 1 = PCM
        writeShortLE(header, 22, channels.toShort())
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate.toInt())
        writeShortLE(header, 32, blockAlign)
        writeShortLE(header, 34, bitsPerSample.toShort())
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalAudioLen.toInt())
        return header
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Short) {
        val v = value.toInt()
        buf[offset] = (v and 0xFF).toByte()
        buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
