package com.smartdialer.app.util

import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogLevel {
    INFO,
    WARN,
    TELECOM,
    ERROR,
    CRASH
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    val formattedDateTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
}

/**
 * Centralized Application Health, Crash & Diagnostic Logger.
 * Automatically catches uncaught exceptions, records telecom events, permissions state,
 * and maintains rotating on-disk and in-memory logs for live app debugging.
 */
object AppAnalysisLogger {

    private const val MAX_IN_MEMORY_LOGS = 300
    private const val LOG_FILE_NAME = "smart_dialer_analysis.log"
    private const val CRASH_FILE_NAME = "smart_dialer_last_crash.log"

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val _crashCount = MutableStateFlow(0)
    val crashCount: StateFlow<Int> = _crashCount.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        appContext = context.applicationContext

        // 1. Install Uncaught Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, thread, throwable)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // 2. Load cached logs from file
        loadLogsFromFile(context)

        log("System", "Smart-Dialer Application initialized on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})", LogLevel.INFO)
    }

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO, throwable: Throwable? = null) {
        val stackTrace = throwable?.let { getStackTraceString(it) }
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )

        addLogEntry(entry)

        if (level == LogLevel.ERROR) {
            _errorCount.value = _errorCount.value + 1
        } else if (level == LogLevel.CRASH) {
            _crashCount.value = _crashCount.value + 1
        }

        // Persist to disk asynchronously
        appContext?.let { ctx ->
            scope.launch {
                persistLogToFile(ctx, entry)
            }
        }
    }

    fun info(tag: String, message: String) = log(tag, message, LogLevel.INFO)
    fun warn(tag: String, message: String) = log(tag, message, LogLevel.WARN)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(tag, message, LogLevel.ERROR, throwable)
    fun telecom(event: String, details: String) = log("Telecom", "[$event] $details", LogLevel.TELECOM)

    private fun recordCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stack = getStackTraceString(throwable)
        val entry = LogEntry(
            level = LogLevel.CRASH,
            tag = "FATAL_CRASH",
            message = "Uncaught Exception in Thread [${thread.name}]: ${throwable.message ?: throwable.javaClass.simpleName}",
            stackTrace = stack
        )

        addLogEntry(entry)
        _crashCount.value = _crashCount.value + 1

        // Synchronous write to crash dump file
        try {
            val crashFile = File(context.filesDir, CRASH_FILE_NAME)
            val report = buildString {
                appendLine("=== FATAL CRASH REPORT ===")
                appendLine("Time: ${entry.formattedDateTime}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
                appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.name} (id: ${thread.id})")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("--- STACK TRACE ---")
                appendLine(stack)
                appendLine("==========================")
            }
            crashFile.writeText(report)
            persistLogToFile(context, entry)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun addLogEntry(entry: LogEntry) {
        val current = _logsFlow.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > MAX_IN_MEMORY_LOGS) {
            current.removeAt(current.lastIndex)
        }
        _logsFlow.value = current
    }

    private fun persistLogToFile(context: Context, entry: LogEntry) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            // Rotate if larger than 500KB
            if (file.exists() && file.length() > 500 * 1024) {
                file.delete()
            }
            val line = buildString {
                append("[${entry.formattedDateTime}] [${entry.level}] [${entry.tag}] ${entry.message}")
                if (!entry.stackTrace.isNullOrBlank()) {
                    append("\n--- StackTrace ---\n${entry.stackTrace}\n------------------")
                }
                append("\n")
            }
            file.appendText(line)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadLogsFromFile(context: Context) {
        scope.launch {
            try {
                val file = File(context.filesDir, LOG_FILE_NAME)
                if (!file.exists()) return@launch

                val lines = file.readLines().takeLast(100)
                var errCount = 0
                var crshCount = 0

                lines.forEach { line ->
                    if (line.contains("[ERROR]")) errCount++
                    if (line.contains("[CRASH]")) crshCount++
                }

                _errorCount.value = errCount
                _crashCount.value = crshCount
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearLogs(context: Context) {
        _logsFlow.value = emptyList()
        _errorCount.value = 0
        _crashCount.value = 0
        scope.launch {
            try {
                File(context.filesDir, LOG_FILE_NAME).delete()
                File(context.filesDir, CRASH_FILE_NAME).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Compiles a comprehensive diagnostic report that can be copied or shared with developers.
     */
    fun generateFullDiagnosticReport(context: Context): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBatteryIgnoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage == context.packageName
        }

        val lastCrashContent = try {
            val cf = File(context.filesDir, CRASH_FILE_NAME)
            if (cf.exists()) cf.readText().trim() else "None"
        } catch (e: Exception) {
            "Error reading crash file: ${e.message}"
        }

        val recentLogs = _logsFlow.value.take(40)

        val isAggressiveOem = listOf("xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo", "huawei", "honor").any {
            Build.MANUFACTURER.lowercase(Locale.ROOT).contains(it) || Build.BRAND.lowercase(Locale.ROOT).contains(it)
        }

        return buildString {
            appendLine("=========================================")
            appendLine(" SMART-DIALER SYSTEM DIAGNOSTIC REPORT")
            appendLine(" Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("=========================================")
            appendLine()
            appendLine("[1. DEVICE & ENVIRONMENT]")
            appendLine("• Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})")
            appendLine("• Board / Product: ${Build.BOARD} / ${Build.PRODUCT}")
            appendLine("• Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("• Build Fingerprint: ${Build.FINGERPRINT}")
            appendLine("• OEM Type: ${if (isAggressiveOem) "Aggressive OEM (Xiaomi/ColorOS/Realme/Vivo)" else "Standard AOSP"}")
            appendLine()
            appendLine("[2. TELECOM & CRITICAL PERMISSIONS]")
            appendLine("• Default Dialer (ROLE_DIALER): ${if (isDefaultDialer) "HELD (ACTIVE)" else "NOT DEFAULT (WARNING)"}")
            appendLine("• Full-Screen Intent (Android 14+): ${if (canFullScreen) "ALLOWED" else "DENIED / RESTRICTED"}")
            appendLine("• Battery Optimizations: ${if (isBatteryIgnoring) "EXEMPT (No Restrictions - Optimal)" else "RESTRICTED (May kill in background)"}")
            appendLine("• Notifications Enabled: ${if (notificationsEnabled) "YES" else "DISABLED"}")
            appendLine()
            appendLine("[3. HEALTH & CRASH SUMMARY]")
            appendLine("• Crashes Recorded: ${_crashCount.value}")
            appendLine("• Errors Recorded: ${_errorCount.value}")
            appendLine("• In-Memory Log Entries: ${_logsFlow.value.size}")
            appendLine()
            appendLine("[4. LAST CRASH DUMP]")
            appendLine(lastCrashContent)
            appendLine()
            appendLine("[5. RECENT LOG TRACE (Newest First)]")
            if (recentLogs.isEmpty()) {
                appendLine("(No recent logs recorded)")
            } else {
                recentLogs.forEach { entry ->
                    appendLine("[${entry.formattedTime}] [${entry.level}] [${entry.tag}] ${entry.message}")
                    if (!entry.stackTrace.isNullOrBlank()) {
                        appendLine("   StackTrace: ${entry.stackTrace.lines().take(5).joinToString("\n   ")}")
                    }
                }
            }
            appendLine("=========================================")
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
