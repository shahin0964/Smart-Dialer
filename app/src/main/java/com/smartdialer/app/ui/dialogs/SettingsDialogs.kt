package com.smartdialer.app.ui.dialogs

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.SimInfo
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.Sim1Blue
import com.smartdialer.app.ui.theme.Sim2Green
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted

// ============================================================
// 1. DUAL SIM SETTINGS DIALOG
// ============================================================
@Composable
fun DualSimSettingsDialog(
    sims: List<SimInfo>,
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var selectedSlot by remember { mutableStateOf(appSettings.preferredSimSlot) }
    var askEveryTime by remember { mutableStateOf(appSettings.askEveryTime) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                DialogHeader(
                    title = "Dual SIM Settings",
                    icon = Icons.Default.SimCard,
                    iconTint = Sim1Blue,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Default Outgoing SIM Card",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Always Ask option
                SimOptionRow(
                    title = "Always Ask",
                    subtitle = "Prompt to choose SIM 1 or SIM 2 before every call",
                    isSelected = selectedSlot == -1,
                    onClick = { selectedSlot = -1 }
                )

                // SIM 1 option
                val sim1 = sims.firstOrNull { it.slotIndex == 0 }
                val sim1Name = sim1?.displayName ?: "SIM 1"
                val sim1Carrier = if (!sim1?.carrierName.isNullOrBlank()) " (${sim1.carrierName})" else ""
                SimOptionRow(
                    title = "SIM 1: $sim1Name$sim1Carrier",
                    subtitle = sim1?.carrierName ?: "Standard cellular",
                    isSelected = selectedSlot == 0,
                    onClick = { selectedSlot = 0 }
                )

                // SIM 2 option
                val sim2 = sims.firstOrNull { it.slotIndex == 1 }
                val sim2Name = sim2?.displayName ?: "SIM 2"
                val sim2Carrier = if (!sim2?.carrierName.isNullOrBlank()) " (${sim2.carrierName})" else ""
                SimOptionRow(
                    title = "SIM 2: $sim2Name$sim2Carrier",
                    subtitle = sim2?.carrierName ?: "Standard cellular",
                    isSelected = selectedSlot == 1,
                    onClick = { selectedSlot = 1 }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Ask every time toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Ask every time on dialer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Text(
                            text = "Show SIM 1 and SIM 2 call buttons",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = askEveryTime,
                        onCheckedChange = { askEveryTime = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        appSettings.preferredSimSlot = selectedSlot
                        appSettings.askEveryTime = askEveryTime
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save SIM Preferences", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 2. CALL RECORDING SETTINGS DIALOG
// ============================================================
@Composable
fun CallRecordingDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var autoRecord by remember { mutableStateOf(appSettings.autoRecord) }
    var recordUnknownOnly by remember { mutableStateOf(appSettings.recordUnknownOnly) }
    var audioFormat by remember { mutableStateOf(appSettings.audioFormat) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            autoRecord = true
        } else {
            autoRecord = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                DialogHeader(
                    title = "Call Recording",
                    icon = Icons.Default.RecordVoiceOver,
                    iconTint = Color(0xFFE11D48),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Record Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Auto Call Recording",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Text(
                            text = "Automatically record all calls",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = autoRecord,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    autoRecord = true
                                }
                            } else {
                                autoRecord = false
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Record Unknown Only
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Record Unknown Numbers Only",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Text(
                            text = "Do not record calls from saved contacts",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = recordUnknownOnly,
                        enabled = autoRecord,
                        onCheckedChange = { recordUnknownOnly = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Format Selection
                Text(
                    text = "Audio Format",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AAC / M4A", "AMR").forEach { format ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (audioFormat == format) PurplePrimary else PurpleUltraLight,
                            modifier = Modifier
                                .clickable { audioFormat = format }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = format,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (audioFormat == format) Color.White else TextDark,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Storage location
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Save Location", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Internal Storage / Recordings / SmartDialer", fontSize = 12.sp, color = TextDark)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        appSettings.autoRecord = autoRecord
                        appSettings.recordUnknownOnly = recordUnknownOnly
                        appSettings.audioFormat = audioFormat
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Recording Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 3. SOUNDS & HAPTICS SETTINGS DIALOG
// ============================================================
@Composable
fun SoundsHapticsDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var dialpadTones by remember { mutableStateOf(appSettings.dialpadTones) }
    var dialpadVibration by remember { mutableStateOf(appSettings.dialpadVibration) }
    var callConnectedVibration by remember { mutableStateOf(appSettings.callConnectedVibration) }
    var callEndedVibration by remember { mutableStateOf(appSettings.callEndedVibration) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                DialogHeader(
                    title = "Sounds & Haptics",
                    icon = Icons.Default.VolumeUp,
                    iconTint = Color(0xFF2563EB),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(16.dp))

                ToggleSettingRow(
                    title = "Dialpad Touch Tones",
                    subtitle = "Play DTMF tones when tapping dialpad",
                    checked = dialpadTones,
                    onCheckedChange = { dialpadTones = it }
                )

                ToggleSettingRow(
                    title = "Dialpad Vibration",
                    subtitle = "Vibrate on keypress & button clicks",
                    checked = dialpadVibration,
                    onCheckedChange = { dialpadVibration = it }
                )

                ToggleSettingRow(
                    title = "Vibrate on Call Connected",
                    subtitle = "Vibrate briefly when the other party answers",
                    checked = callConnectedVibration,
                    onCheckedChange = { callConnectedVibration = it }
                )

                ToggleSettingRow(
                    title = "Vibrate on Call Ended",
                    subtitle = "Vibrate briefly when the call disconnects",
                    checked = callEndedVibration,
                    onCheckedChange = { callEndedVibration = it }
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        appSettings.dialpadTones = dialpadTones
                        appSettings.dialpadVibration = dialpadVibration
                        appSettings.callConnectedVibration = callConnectedVibration
                        appSettings.callEndedVibration = callEndedVibration
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Sounds & Haptics", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 4. BLOCKED NUMBERS & SPAM PROTECTION DIALOG
// ============================================================
@Composable
fun BlockedNumbersDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var blockUnknown by remember { mutableStateOf(appSettings.blockUnknownNumbers) }
    var spamProtection by remember { mutableStateOf(appSettings.spamProtectionEnabled) }
    var newNumberInput by remember { mutableStateOf("") }
    var blockedList by remember { mutableStateOf(appSettings.blockedNumbers.toList()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                DialogHeader(
                    title = "Blocked & Spam Protection",
                    icon = Icons.Default.Security,
                    iconTint = Color(0xFF10B981),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                ToggleSettingRow(
                    title = "Block Unknown Numbers",
                    subtitle = "Block calls from private or hidden callers",
                    checked = blockUnknown,
                    onCheckedChange = { blockUnknown = it }
                )

                ToggleSettingRow(
                    title = "Spam Protection",
                    subtitle = "Identify potential telemarketers and fraud",
                    checked = spamProtection,
                    onCheckedChange = { spamProtection = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Blocked Phone Numbers",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Add number input + button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newNumberInput,
                        onValueChange = { newNumberInput = it },
                        placeholder = { Text("e.g. +8801700000000", color = TextLightGray, fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedIndicatorColor = PurplePrimary,
                            unfocusedIndicatorColor = Color(0xFFE2E8F0)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newNumberInput.isNotBlank()) {
                                appSettings.addBlockedNumber(newNumberInput)
                                blockedList = appSettings.blockedNumbers.toList()
                                newNumberInput = ""
                                onSaved()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of blocked numbers
                if (blockedList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "No phone numbers blocked yet.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(blockedList) { number ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = number, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                                }
                                IconButton(
                                    onClick = {
                                        appSettings.removeBlockedNumber(number)
                                        blockedList = appSettings.blockedNumbers.toList()
                                        onSaved()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        appSettings.blockUnknownNumbers = blockUnknown
                        appSettings.spamProtectionEnabled = spamProtection
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Block & Spam Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 5. ABOUT DIALOG
// ============================================================
@Composable
fun AboutSmartDialerDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurplePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Smart Dialer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Text(
                    text = "Version 1.0.6 (Native Jetpack Compose)",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PurpleUltraLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "• Dual SIM (SIM 1 & SIM 2 Outgoing)", fontSize = 12.sp, color = TextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "• PortSIP-Style IP Calling & VoIP", fontSize = 12.sp, color = TextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "• Call Recording & Storage", fontSize = 12.sp, color = TextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "• Spam Protection & Blocked Numbers", fontSize = 12.sp, color = TextDark)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// Helper components
@Composable
private fun DialogHeader(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SimOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
            Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
        )
    }
}
