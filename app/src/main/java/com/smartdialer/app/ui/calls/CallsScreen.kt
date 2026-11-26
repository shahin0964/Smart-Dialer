package com.smartdialer.app.ui.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.CallLogItem
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.model.GroupedCallLog
import com.smartdialer.app.data.repository.CallNote
import com.smartdialer.app.data.repository.CallNotesRepository
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.components.ContactAvatar
import com.smartdialer.app.ui.components.PermissionCard
import com.smartdialer.app.ui.components.SamsungSwipeItem
import com.smartdialer.app.ui.dialogs.CallAccountPickerDialog
import com.smartdialer.app.ui.dialogs.CallAnalyticsDialog
import com.smartdialer.app.utils.SmsHelper
import com.smartdialer.app.utils.WhatsAppHelper
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.CallMissedRed
import com.smartdialer.app.ui.theme.CallOutgoingBlue
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted

@Composable
fun CallsScreen(
    viewModel: CallsViewModel
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingCallName by remember { mutableStateOf<String?>(null) }

    val currentPendingNumber = pendingCallNumber
    val currentPendingName = pendingCallName
    if (!currentPendingNumber.isNullOrBlank()) {
        CallAccountPickerDialog(
            phoneNumber = currentPendingNumber,
            contactName = currentPendingName,
            onCallSim1 = { numberToCall ->
                try {
                    CallManager.initiateCall(context, numberToCall, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onCallSim2 = { numberToCall ->
                try {
                    CallManager.initiateCall(context, numberToCall, 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onCallIp = { numberToCall ->
                try {
                    CallManager.initiateIpCall(context, numberToCall)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onDismiss = {
                pendingCallNumber = null
                pendingCallName = null
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.READ_CALL_LOG] == true
        if (hasPermission) {
            viewModel.loadCallLogs()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadCallLogs()
        }
    }

    val groupedCallLogs by viewModel.groupedCallLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var selectedGroupForHistory by remember { mutableStateOf<GroupedCallLog?>(null) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var notesVersion by remember { mutableStateOf(0) }
    val notesByKey = remember(groupedCallLogs, notesVersion) {
        CallNotesRepository.getAllNotes(context).associateBy { it.normalizedKey }
    }

    if (showAnalyticsDialog) {
        CallAnalyticsDialog(onDismiss = { showAnalyticsDialog = false })
    }

    // Dynamic update for currently opened detail dialog
    val currentSelectedGroup = selectedGroupForHistory?.let { cur ->
        groupedCallLogs.find { it.groupKey == cur.groupKey }
    }

    if (selectedGroupForHistory != null && currentSelectedGroup != null && currentSelectedGroup.calls.isNotEmpty()) {
        CallHistoryDetailDialog(
            group = currentSelectedGroup,
            onDismiss = { selectedGroupForHistory = null },
            onCallClick = { number, name ->
                pendingCallNumber = number
                pendingCallName = name
            },
            onDeleteCall = { callId ->
                viewModel.deleteLog(callId)
            },
            onDeleteAllHistory = { number ->
                viewModel.deleteLogsForNumber(number)
                selectedGroupForHistory = null
            },
            onNoteUpdated = { notesVersion++ }
        )
    }

    val theme = LocalAppTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // Sub-header: Call History Title + Analytics + Search + Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Call History",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showAnalyticsDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Call Analytics",
                        tint = theme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleSearchVisibility() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = theme.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { filterMenuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter",
                            tint = if (activeFilter != CallsHistoryFilter.ALL) theme.primary else theme.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        modifier = Modifier.background(theme.cardBackground)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Calls", color = theme.textPrimary, fontWeight = if (activeFilter == CallsHistoryFilter.ALL) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallsHistoryFilter.ALL)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Incoming", color = CallIncomingGreen) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallsHistoryFilter.INCOMING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Outgoing", color = CallOutgoingBlue) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallsHistoryFilter.OUTGOING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Missed", color = CallMissedRed) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallsHistoryFilter.MISSED)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("SIP / IP Calls", color = Color(0xFF8B5CF6), fontWeight = if (activeFilter == CallsHistoryFilter.SIP) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallsHistoryFilter.SIP)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("SIM Calls", color = theme.primary, fontWeight = if (activeFilter == CallsHistoryFilter.SIM) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallsHistoryFilter.SIM)
                            }
                        )
                    }
                }
            }
        }

        // Search Input Bar if expanded
        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search call history...", color = theme.textMuted) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = theme.cardBackground,
                    unfocusedContainerColor = theme.cardBackground,
                    focusedTextColor = theme.textPrimary,
                    unfocusedTextColor = theme.textPrimary,
                    focusedIndicatorColor = theme.primary,
                    unfocusedIndicatorColor = theme.divider
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!hasPermission) {
            PermissionCard(
                title = "Call Log Permission Required",
                description = "To view and manage your real call history, Smart Dialer needs permission to read call logs on your device.",
                buttonText = "Grant Call Log Permission",
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.WRITE_CALL_LOG,
                            Manifest.permission.CALL_PHONE
                        )
                    )
                }
            )
        } else if (groupedCallLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = TextLightGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching calls found" else "No call history found on device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }
            }
        } else {
            // Group calls by Today, Yesterday, Other dates (based on their latest call)
            val todayGroups = groupedCallLogs.filter { it.isToday }
            val yesterdayGroups = groupedCallLogs.filter { it.isYesterday }
            val olderGroups = groupedCallLogs.filter { !it.isToday && !it.isYesterday }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                if (todayGroups.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Today")
                    }
                    items(todayGroups, key = { it.groupKey }) { group ->
                        SamsungSwipeItem(
                            onSwipeRight = {
                                pendingCallNumber = group.primaryNumber
                                pendingCallName = group.displayName
                            },
                            onSwipeLeft = {
                                SmsHelper.openSms(context, group.primaryNumber)
                            }
                        ) {
                            val note = notesByKey[CallNotesRepository.normalizeNumberKey(group.primaryNumber)]
                            GroupedCallLogRow(
                                group = group,
                                note = note,
                                onRowClick = {
                                    selectedGroupForHistory = group
                                },
                                onCallClick = {
                                    pendingCallNumber = group.primaryNumber
                                    pendingCallName = group.displayName
                                }
                            )
                        }
                    }
                }

                if (yesterdayGroups.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Yesterday")
                    }
                    items(yesterdayGroups, key = { it.groupKey }) { group ->
                        SamsungSwipeItem(
                            onSwipeRight = {
                                pendingCallNumber = group.primaryNumber
                                pendingCallName = group.displayName
                            },
                            onSwipeLeft = {
                                SmsHelper.openSms(context, group.primaryNumber)
                            }
                        ) {
                            val note = notesByKey[CallNotesRepository.normalizeNumberKey(group.primaryNumber)]
                            GroupedCallLogRow(
                                group = group,
                                note = note,
                                onRowClick = {
                                    selectedGroupForHistory = group
                                },
                                onCallClick = {
                                    pendingCallNumber = group.primaryNumber
                                    pendingCallName = group.displayName
                                }
                            )
                        }
                    }
                }

                if (olderGroups.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Older")
                    }
                    items(olderGroups, key = { it.groupKey }) { group ->
                        SamsungSwipeItem(
                            onSwipeRight = {
                                pendingCallNumber = group.primaryNumber
                                pendingCallName = group.displayName
                            },
                            onSwipeLeft = {
                                SmsHelper.openSms(context, group.primaryNumber)
                            }
                        ) {
                            val note = notesByKey[CallNotesRepository.normalizeNumberKey(group.primaryNumber)]
                            GroupedCallLogRow(
                                group = group,
                                note = note,
                                onRowClick = {
                                    selectedGroupForHistory = group
                                },
                                onCallClick = {
                                    pendingCallNumber = group.primaryNumber
                                    pendingCallName = group.displayName
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    val theme = LocalAppTheme.current
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = theme.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun GroupedCallLogRow(
    group: GroupedCallLog,
    note: CallNote? = null,
    onRowClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    val latest = group.latestCall

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Circular Avatar with Photo or Initial
            ContactAvatar(
                name = group.displayName,
                photoUri = group.photoUri,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name + Count Badge + Spam Tag + Call Direction Icon + Phone Number
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (group.isSpam) Color(0xFFDC2626) else theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Total call count badge if more than 1 call
                    if (group.totalCount > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "(${group.totalCount})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (latest.isIpCall) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "SIP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF8B5CF6),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (group.isSpam) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(0.5.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = group.spamCategory ?: "SPAM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, tint) = when (latest.type) {
                        CallType.INCOMING -> Icons.Default.CallReceived to CallIncomingGreen
                        CallType.OUTGOING -> Icons.Default.CallMade to CallOutgoingBlue
                        CallType.MISSED -> Icons.Default.CallMissed to CallMissedRed
                        CallType.REJECTED -> Icons.Default.CallMissed to CallMissedRed
                        CallType.UNKNOWN -> Icons.Default.Call to TextLightGray
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = group.primaryNumber,
                        fontSize = 13.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (note != null && note.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${note.tag}: ${note.note}",
                            fontSize = 11.sp,
                            color = theme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Timestamp & Purple Call Icon Button
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = latest.formattedTime,
                fontSize = 12.sp,
                color = TextLightGray
            )

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CallHistoryDetailDialog(
    group: GroupedCallLog,
    onDismiss: () -> Unit,
    onCallClick: (number: String, name: String?) -> Unit,
    onDeleteCall: (callId: String) -> Unit,
    onDeleteAllHistory: (number: String) -> Unit,
    onNoteUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val existingNote = remember(group.primaryNumber) {
        CallNotesRepository.getNoteForNumber(context, group.primaryNumber)
    }
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteDraft by remember(group.primaryNumber) { mutableStateOf(existingNote?.note ?: "") }
    var currentNoteDisplay by remember(group.primaryNumber) { mutableStateOf(existingNote?.note ?: "") }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Call History", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all call history records for ${group.displayName}?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAllHistory(group.primaryNumber)
                    }
                ) {
                    Text("Delete All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            val theme = LocalAppTheme.current

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {}, // Prevent dismiss on inner click
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Top Bar with Title & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Call Details & History",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textMuted
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = theme.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Contact Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(
                            name = group.displayName,
                            photoUri = group.photoUri,
                            size = 56.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (group.isSpam) Color(0xFFDC2626) else theme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = group.primaryNumber,
                                fontSize = 14.sp,
                                color = theme.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row (Call, SMS, Copy, Delete All)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Call Action
                        Button(
                            onClick = {
                                onCallClick(group.primaryNumber, group.displayName)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // WhatsApp Direct Chat Action (No Save Needed)
                        Surface(
                            onClick = {
                                WhatsAppHelper.openWhatsAppChat(context, group.primaryNumber)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                            }
                        }

                        // SMS Action
                        Surface(
                            onClick = {
                                SmsHelper.openSms(context, group.primaryNumber)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = theme.surface,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Message, contentDescription = "SMS", tint = theme.primary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Note / Reminder Action
                        Surface(
                            onClick = { showNoteEditor = !showNoteEditor },
                            shape = RoundedCornerShape(10.dp),
                            color = if (showNoteEditor || currentNoteDisplay.isNotBlank()) theme.primaryUltraLight else theme.surface,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.EditNote, contentDescription = "Call Note", tint = theme.primary, modifier = Modifier.size(19.dp))
                            }
                        }

                        // Copy Action
                        Surface(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(group.primaryNumber))
                                Toast.makeText(context, "Number copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = theme.surface,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = theme.textPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Delete All History Action
                        Surface(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (showNoteEditor || currentNoteDisplay.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, theme.divider),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (!showNoteEditor) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showNoteEditor = true },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Note: $currentNoteDisplay",
                                            fontSize = 12.sp,
                                            color = theme.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Edit",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.primary
                                        )
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = noteDraft,
                                        onValueChange = { noteDraft = it },
                                        placeholder = { Text("Write note for this number...", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                onClick = {
                                                    CallNotesRepository.scheduleReminder(context, group.primaryNumber, group.displayName, 30, noteDraft)
                                                    Toast.makeText(context, "Callback reminder in 30m", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Remind 30m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                                }
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                CallNotesRepository.saveNote(context, group.primaryNumber, noteDraft)
                                                currentNoteDisplay = noteDraft.trim()
                                                showNoteEditor = false
                                                onNoteUpdated()
                                                Toast.makeText(context, "Note updated", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                                        ) {
                                            Text("Save", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Summary Stats Card
                    val incomingCount = group.calls.count { it.type == CallType.INCOMING }
                    val outgoingCount = group.calls.count { it.type == CallType.OUTGOING }
                    val missedCount = group.calls.count { it.type == CallType.MISSED || it.type == CallType.REJECTED }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, theme.divider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Duration", fontSize = 11.sp, color = theme.textMuted)
                                Text(group.totalDurationFormatted, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("↙ $incomingCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CallIncomingGreen)
                                Text("↗ $outgoingCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CallOutgoingBlue)
                                Text("✕ $missedCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CallMissedRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "History (${group.totalCount} calls)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chronological Call List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(group.calls, key = { it.id }) { call ->
                            SingleCallHistoryItem(
                                call = call,
                                onDelete = { onDeleteCall(call.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleCallHistoryItem(
    call: CallLogItem,
    onDelete: () -> Unit
) {
    val theme = LocalAppTheme.current

    val (icon, tint, bg) = when (call.type) {
        CallType.INCOMING -> Triple(Icons.Default.CallReceived, CallIncomingGreen, Color(0xFFECFDF5).copy(alpha = 0.2f))
        CallType.OUTGOING -> Triple(Icons.Default.CallMade, CallOutgoingBlue, Color(0xFFEFF6FF).copy(alpha = 0.2f))
        CallType.MISSED -> Triple(Icons.Default.CallMissed, CallMissedRed, Color(0xFFFEF2F2).copy(alpha = 0.2f))
        CallType.REJECTED -> Triple(Icons.Default.CallMissed, Color(0xFFF59E0B), Color(0xFFFFFBEB).copy(alpha = 0.2f))
        CallType.UNKNOWN -> Triple(Icons.Default.Call, theme.textMuted, theme.surface)
    }

    val typeLabel = when (call.type) {
        CallType.INCOMING -> "Incoming"
        CallType.OUTGOING -> "Outgoing"
        CallType.MISSED -> "Missed"
        CallType.REJECTED -> "Declined"
        CallType.UNKNOWN -> "Call"
    }

    val dateLabel = when {
        call.isToday -> "Today, ${call.formattedTime}"
        call.isYesterday -> "Yesterday, ${call.formattedTime}"
        else -> "${call.formattedDate}, ${call.formattedTime}"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.divider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = bg,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = typeLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Duration Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (call.durationSeconds > 0) theme.primaryUltraLight else theme.surface
                        ) {
                            Text(
                                text = call.durationFormatted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (call.durationSeconds > 0) theme.primary else theme.textMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateLabel,
                        fontSize = 12.sp,
                        color = theme.textMuted
                    )
                }
            }

            // Single Delete Icon
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete call",
                    tint = theme.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
