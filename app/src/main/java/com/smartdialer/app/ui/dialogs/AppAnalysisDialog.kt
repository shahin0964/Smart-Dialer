package com.smartdialer.app.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.util.AppAnalysisLogger
import com.smartdialer.app.util.LogEntry
import com.smartdialer.app.util.LogLevel

enum class AnalysisFilter {
    ALL,
    CRASHES,
    ERRORS,
    TELECOM
}

@Composable
fun AppAnalysisDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    val allLogs by AppAnalysisLogger.logsFlow.collectAsState()
    val crashCount by AppAnalysisLogger.crashCount.collectAsState()
    val errorCount by AppAnalysisLogger.errorCount.collectAsState()

    var selectedFilter by remember { mutableStateOf(AnalysisFilter.ALL) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val filteredLogs = remember(allLogs, selectedFilter, refreshTrigger) {
        when (selectedFilter) {
            AnalysisFilter.ALL -> allLogs
            AnalysisFilter.CRASHES -> allLogs.filter { it.level == LogLevel.CRASH }
            AnalysisFilter.ERRORS -> allLogs.filter { it.level == LogLevel.ERROR }
            AnalysisFilter.TELECOM -> allLogs.filter { it.level == LogLevel.TELECOM }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = theme.cardBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (crashCount > 0) Color(0xFFEF4444) else if (errorCount > 0) Color(0xFFF59E0B) else theme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (crashCount > 0) Icons.Default.Warning else Icons.Default.BugReport,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "App Analysis & Bug Logger",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Live Error Detection & Crash Diagnosis",
                                fontSize = 12.sp,
                                color = theme.textMuted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Health Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (crashCount > 0) Color(0xFFFEF2F2).copy(alpha = 0.2f) else if (errorCount > 0) Color(0xFFFFFBEB).copy(alpha = 0.2f) else Color(0xFFECFDF5).copy(alpha = 0.2f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (crashCount > 0) Color(0xFFEF4444) else if (errorCount > 0) Color(0xFFF59E0B) else CallIncomingGreen
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (crashCount > 0) Icons.Default.Warning else if (errorCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (crashCount > 0) Color(0xFFEF4444) else if (errorCount > 0) Color(0xFFF59E0B) else CallIncomingGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (crashCount > 0) "$crashCount Crash(es) Detected!" else if (errorCount > 0) "$errorCount Error(s) Logged" else "System Health: Normal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (crashCount > 0) Color(0xFFEF4444) else if (errorCount > 0) Color(0xFFF59E0B) else CallIncomingGreen
                                )
                                Text(
                                    text = "Total Entries: ${allLogs.size} logs",
                                    fontSize = 12.sp,
                                    color = theme.textMuted
                                )
                            }
                        }

                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar: Copy Full Report & Share & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val report = AppAnalysisLogger.generateFullDiagnosticReport(context)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SmartDialer Report", report))
                            Toast.makeText(context, "Diagnostic report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Report", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val report = AppAnalysisLogger.generateFullDiagnosticReport(context)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, report)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = theme.textPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp, color = theme.textPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            AppAnalysisLogger.clearLogs(context)
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontSize = 13.sp, color = Color(0xFFEF4444))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnalysisFilter.values().forEach { filter ->
                        val isSelected = (selectedFilter == filter)
                        val label = when (filter) {
                            AnalysisFilter.ALL -> "All (${allLogs.size})"
                            AnalysisFilter.CRASHES -> "Crashes ($crashCount)"
                            AnalysisFilter.ERRORS -> "Errors ($errorCount)"
                            AnalysisFilter.TELECOM -> "Telecom"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) theme.primary else theme.background)
                                .clickable { selectedFilter = filter }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else theme.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Logs LazyColumn
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CallIncomingGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No logs in this category",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Everything is running smoothly!",
                                fontSize = 12.sp,
                                color = theme.textMuted
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { logItem ->
                            LogItemCard(entry = logItem)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(entry: LogEntry) {
    val theme = LocalAppTheme.current
    var isExpanded by remember { mutableStateOf(false) }

    val badgeColor = when (entry.level) {
        LogLevel.CRASH -> Color(0xFFEF4444)
        LogLevel.ERROR -> Color(0xFFF97316)
        LogLevel.WARN -> Color(0xFFEAB308)
        LogLevel.TELECOM -> Color(0xFF3B82F6)
        LogLevel.INFO -> Color(0xFF10B981)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = theme.background)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = entry.level.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.tag,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary
                    )
                }

                Text(
                    text = entry.formattedTime,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = theme.textMuted
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = entry.message,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = theme.textPrimary
            )

            if (!entry.stackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isExpanded) "Hide StackTrace" else "View StackTrace",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = entry.stackTrace,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF87171)
                        )
                    }
                }
            }
        }
    }
}
