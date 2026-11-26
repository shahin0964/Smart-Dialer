package com.smartdialer.app.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartdialer.app.data.model.CallLogItem
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.repository.CallLogRepository
import com.smartdialer.app.data.repository.IpCallStatsRepository
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.CallMissedRed
import com.smartdialer.app.ui.theme.CallOutgoingBlue
import com.smartdialer.app.ui.theme.IpCallPurple
import com.smartdialer.app.ui.theme.LocalAppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class DailyTalkStat(
    val dayLabel: String,
    val totalSeconds: Long,
    val callCount: Int
)

private data class TopContactRow(
    val title: String,
    val number: String,
    val totalSeconds: Long,
    val callCount: Int
)

@Composable
fun CallAnalyticsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    var selectedDays by remember { mutableIntStateOf(7) } // 7, 30, or 0 (All)
    var isLoading by remember { mutableStateOf(true) }
    var allLogs by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var ipCallCount by remember { mutableIntStateOf(0) }
    var ipTalkSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        isLoading = true
        allLogs = CallLogRepository(context).getCallLogs()
        val ipHistory = IpCallStatsRepository.getCallHistory(context)
        ipCallCount = ipHistory.size
        ipTalkSeconds = ipHistory.sumOf { it.durationSeconds }
        isLoading = false
    }

    val filteredLogs = remember(allLogs, selectedDays) {
        if (selectedDays == 0) {
            allLogs
        } else {
            val cutoff = System.currentTimeMillis() - (selectedDays * 24L * 3600L * 1000L)
            allLogs.filter { it.dateMillis >= cutoff }
        }
    }

    val totalCalls = filteredLogs.size
    val totalSeconds = filteredLogs.sumOf { it.durationSeconds }
    val incomingCount = filteredLogs.count { it.type == CallType.INCOMING }
    val outgoingCount = filteredLogs.count { it.type == CallType.OUTGOING }
    val missedCount = filteredLogs.count { it.type == CallType.MISSED || it.type == CallType.REJECTED }
    val avgDuration = if (totalCalls > 0) totalSeconds / totalCalls else 0L

    // Last 7 days bar chart data
    val dailyStats = remember(allLogs) {
        val sdf = SimpleDateFormat("EEE", Locale.getDefault())
        val list = mutableListOf<DailyTalkStat>()
        for (daysAgo in 6 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val yr = cal.get(Calendar.YEAR)
            val dayOfYr = cal.get(Calendar.DAY_OF_YEAR)
            val label = sdf.format(cal.time)
            val matching = allLogs.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                c.get(Calendar.YEAR) == yr && c.get(Calendar.DAY_OF_YEAR) == dayOfYr
            }
            list.add(
                DailyTalkStat(
                    dayLabel = label,
                    totalSeconds = matching.sumOf { it.durationSeconds },
                    callCount = matching.size
                )
            )
        }
        list
    }

    // Top 5 Contacts
    val topContacts = remember(filteredLogs) {
        filteredLogs.groupBy { log ->
            val digits = log.phoneNumber.filter { it.isDigit() }
            if (digits.length >= 10) digits.takeLast(10) else log.phoneNumber
        }.values.map { group ->
            val first = group.first()
            TopContactRow(
                title = first.name?.takeIf { it.isNotBlank() } ?: first.phoneNumber,
                number = first.phoneNumber,
                totalSeconds = group.sumOf { it.durationSeconds },
                callCount = group.size
            )
        }.sortedByDescending { it.totalSeconds }.take(5)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = theme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call Analytics Dashboard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Period Filter Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 Days", 30 to "30 Days", 0 to "All Time").forEach { (days, label) ->
                        val active = selectedDays == days
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) theme.primary else theme.surface,
                            border = BorderStroke(1.dp, if (active) theme.primary else theme.divider),
                            modifier = Modifier.clickable { selectedDays = days }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else theme.textPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.primary)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top KPI Cards Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                title = "Total Calls",
                                value = totalCalls.toString(),
                                accent = theme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                title = "Total Talk Time",
                                value = formatTalkTime(totalSeconds),
                                accent = CallIncomingGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiCard(
                                title = "Avg Call Length",
                                value = formatTalkTime(avgDuration),
                                accent = CallOutgoingBlue,
                                modifier = Modifier.weight(1f)
                            )
                            KpiCard(
                                title = "IP VoIP Calls",
                                value = "$ipCallCount (${formatTalkTime(ipTalkSeconds)})",
                                accent = IpCallPurple,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Call Direction Breakdown Bar
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, theme.divider),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Call Breakdown",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                val safeTotal = totalCalls.coerceAtLeast(1).toFloat()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.divider)
                                ) {
                                    if (incomingCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight((incomingCount / safeTotal).coerceAtLeast(0.02f))
                                                .fillMaxHeight()
                                                .background(CallIncomingGreen)
                                        )
                                    }
                                    if (outgoingCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight((outgoingCount / safeTotal).coerceAtLeast(0.02f))
                                                .fillMaxHeight()
                                                .background(CallOutgoingBlue)
                                        )
                                    }
                                    if (missedCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight((missedCount / safeTotal).coerceAtLeast(0.02f))
                                                .fillMaxHeight()
                                                .background(CallMissedRed)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    LegendChip("Incoming", incomingCount, CallIncomingGreen)
                                    LegendChip("Outgoing", outgoingCount, CallOutgoingBlue)
                                    LegendChip("Missed", missedCount, CallMissedRed)
                                }
                            }
                        }

                        // 7-Day Talk Time Bar Chart
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, theme.divider),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Last 7 Days Talk Time",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                val maxSec = (dailyStats.maxOfOrNull { it.totalSeconds } ?: 1L).coerceAtLeast(1L)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    dailyStats.forEach { day ->
                                        val ratio = (day.totalSeconds.toFloat() / maxSec.toFloat()).coerceIn(0.05f, 1f)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (day.totalSeconds > 0) "${(day.totalSeconds / 60).coerceAtLeast(1)}m" else "0",
                                                fontSize = 9.sp,
                                                color = theme.textMuted
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(18.dp)
                                                    .height((72f * ratio).dp)
                                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                    .background(if (day.totalSeconds > 0) theme.primary else theme.divider)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = day.dayLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = theme.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Top 5 Contacts
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, theme.divider),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Most Talked Contacts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.textPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (topContacts.isEmpty()) {
                                    Text("No call data in this range", fontSize = 12.sp, color = theme.textMuted)
                                } else {
                                    topContacts.forEachIndexed { idx, row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = theme.primaryUltraLight,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${idx + 1}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = theme.primary
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = row.title,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = theme.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${row.callCount} calls",
                                                        fontSize = 11.sp,
                                                        color = theme.textMuted
                                                    )
                                                }
                                            }
                                            Text(
                                                text = formatTalkTime(row.totalSeconds),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.divider),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontSize = 11.sp, color = theme.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LegendChip(label: String, count: Int, color: Color) {
    val theme = LocalAppTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$label ($count)",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.textPrimary
        )
    }
}

private fun formatTalkTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}
