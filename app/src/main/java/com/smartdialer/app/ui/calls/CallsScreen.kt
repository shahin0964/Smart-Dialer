package com.smartdialer.app.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.CallLogItem
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.components.ContactAvatar
import com.smartdialer.app.ui.components.PermissionCard
import com.smartdialer.app.ui.dialogs.CallAccountPickerDialog
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.CallMissedRed
import com.smartdialer.app.ui.theme.CallOutgoingBlue
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

    val callLogs by viewModel.callLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    var filterMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
    ) {
        // Sub-header: Call History Title + Search + Filter
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
                color = TextDark
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.toggleSearchVisibility() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
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
                            tint = if (activeFilter != null) PurplePrimary else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Calls", fontWeight = if (activeFilter == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Incoming", color = CallIncomingGreen) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallType.INCOMING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Outgoing", color = CallOutgoingBlue) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallType.OUTGOING)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Missed", color = CallMissedRed) },
                            onClick = {
                                filterMenuExpanded = false
                                viewModel.setFilter(CallType.MISSED)
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
                placeholder = { Text("Search call history...", color = TextLightGray) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = PurplePrimary,
                    unfocusedIndicatorColor = Color(0xFFE2E8F0)
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
        } else if (callLogs.isEmpty()) {
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
            // Group calls by Today, Yesterday, Other dates
            val todayCalls = callLogs.filter { it.isToday }
            val yesterdayCalls = callLogs.filter { it.isYesterday }
            val olderCalls = callLogs.filter { !it.isToday && !it.isYesterday }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                if (todayCalls.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Today")
                    }
                    items(todayCalls, key = { it.id }) { call ->
                        CallLogRow(
                            item = call,
                            onCallClick = {
                                pendingCallNumber = call.phoneNumber
                                pendingCallName = call.displayName
                            }
                        )
                    }
                }

                if (yesterdayCalls.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Yesterday")
                    }
                    items(yesterdayCalls, key = { it.id }) { call ->
                        CallLogRow(
                            item = call,
                            onCallClick = {
                                pendingCallNumber = call.phoneNumber
                                pendingCallName = call.displayName
                            }
                        )
                    }
                }

                if (olderCalls.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Older")
                    }
                    items(olderCalls, key = { it.id }) { call ->
                        CallLogRow(
                            item = call,
                            onCallClick = {
                                pendingCallNumber = call.phoneNumber
                                pendingCallName = call.displayName
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun CallLogRow(
    item: CallLogItem,
    onCallClick: () -> Unit
) {
    val avatarColors = listOf(
        Color(0xFFEDE9FE) to Color(0xFF6D28D9), // Purple
        Color(0xFFDBEAFE) to Color(0xFF1D4ED8), // Blue
        Color(0xFFDCFCE7) to Color(0xFF15803D), // Green
        Color(0xFFFEF3C7) to Color(0xFFB45309)  // Amber
    )
    val colorPair = avatarColors[Math.abs(item.displayName.hashCode()) % avatarColors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallClick() }
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
                name = item.displayName,
                photoUri = item.photoUri,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name + Spam Tag + Call Direction Icon + Phone Number
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isSpam) Color(0xFFDC2626) else TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.isSpam) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = item.spamCategory ?: "SPAM",
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
                    val (icon, tint) = when (item.type) {
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
                        text = item.phoneNumber,
                        fontSize = 13.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Timestamp & Purple Call Icon Button
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.formattedTime,
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
