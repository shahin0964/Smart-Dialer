package com.smartdialer.app.ui.dialogs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import com.smartdialer.app.data.callerid.CallerIdResult
import com.smartdialer.app.data.callerid.CallerIdService
import com.smartdialer.app.data.callerid.TruecallerAuthService
import com.smartdialer.app.data.callerid.TruecallerOtpSession
import androidx.core.app.NotificationManagerCompat
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.data.repository.VcfManager
import com.smartdialer.app.telecom.CallManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import com.smartdialer.app.data.model.SpamProtectionLevel
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
import androidx.compose.ui.text.style.TextOverflow
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
    var blockHidden by remember { mutableStateOf(appSettings.blockHiddenNumbers) }
    var blockUnknown by remember { mutableStateOf(appSettings.blockUnknownNumbers) }
    var silenceUnknown by remember { mutableStateOf(appSettings.silenceUnknownCallers) }
    var spamProtection by remember { mutableStateOf(appSettings.spamProtectionEnabled) }
    var selectedLayer by remember { mutableStateOf(appSettings.spamProtectionLevel) }
    var newNumberInput by remember { mutableStateOf("") }
    var blockedList by remember { mutableStateOf(appSettings.blockedNumbers.toList()) }
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                DialogHeader(
                    title = "Spam & Security",
                    icon = Icons.Default.Security,
                    iconTint = Color(0xFF10B981),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Master Spam Protection Toggle
                ToggleSettingRow(
                    title = "Spam Protection Engine",
                    subtitle = "Advanced 3-layer fraud & robocall defense",
                    checked = spamProtection,
                    onCheckedChange = { spamProtection = it }
                )

                if (spamProtection) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Protection Layers (সুরক্ষার লেয়ার নির্বাচন করুন):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Layer 1: BASIC
                    SpamLayerCard(
                        level = SpamProtectionLevel.BASIC,
                        isSelected = selectedLayer == SpamProtectionLevel.BASIC,
                        accentColor = Color(0xFF0284C7),
                        tag = "Layer 1",
                        onClick = { selectedLayer = SpamProtectionLevel.BASIC }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Layer 2: SMART BALANCED
                    SpamLayerCard(
                        level = SpamProtectionLevel.SMART,
                        isSelected = selectedLayer == SpamProtectionLevel.SMART,
                        accentColor = PurplePrimary,
                        tag = "Layer 2 • Recommended",
                        onClick = { selectedLayer = SpamProtectionLevel.SMART }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Layer 3: MAXIMUM STRICT
                    SpamLayerCard(
                        level = SpamProtectionLevel.MAXIMUM,
                        isSelected = selectedLayer == SpamProtectionLevel.MAXIMUM,
                        accentColor = Color(0xFFDC2626),
                        tag = "Layer 3 • Maximum",
                        onClick = { selectedLayer = SpamProtectionLevel.MAXIMUM }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Additional Filtering Options",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))

                ToggleSettingRow(
                    title = "Block Hidden & Private Calls",
                    subtitle = "Auto-reject calls without caller ID",
                    checked = blockHidden,
                    onCheckedChange = { blockHidden = it }
                )

                ToggleSettingRow(
                    title = "Block Unknown / Unsaved Callers",
                    subtitle = "Auto-reject calls from numbers not saved in contacts",
                    checked = blockUnknown,
                    onCheckedChange = { blockUnknown = it }
                )

                ToggleSettingRow(
                    title = "Silence Unsaved Numbers",
                    subtitle = "Mute ringtone for callers not in Contacts",
                    checked = silenceUnknown,
                    onCheckedChange = { silenceUnknown = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Blocked Phone Numbers (${blockedList.size})",
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

                Spacer(modifier = Modifier.height(8.dp))

                // List of blocked numbers
                if (blockedList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "No phone numbers blocked yet.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (number in blockedList) {
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

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        appSettings.blockHiddenNumbers = blockHidden
                        appSettings.blockUnknownNumbers = blockUnknown
                        appSettings.silenceUnknownCallers = silenceUnknown
                        appSettings.spamProtectionEnabled = spamProtection
                        appSettings.spamProtectionLevel = selectedLayer
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Spam & Security Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SpamLayerCard(
    level: SpamProtectionLevel,
    isSelected: Boolean,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.08f) else Color(0xFFF8FAFC),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleek selection circle
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        width = if (isSelected) 5.dp else 1.5.dp,
                        color = if (isSelected) accentColor else Color(0xFFCBD5E1),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = level.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) accentColor else TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) accentColor else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = tag,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            color = if (isSelected) Color.White else TextMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = level.subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) accentColor.copy(alpha = 0.9f) else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================
// 5. ABOUT DIALOG
// ============================================================
@Composable
fun AboutSmartDialerDialog(
    onDismiss: () -> Unit,
    onCheckUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentVersion = remember {
        try {
            val pInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
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
                    text = "Version $currentVersion (Official Release)",
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
                        Text(text = "• 3-Layer Spam Protection Engine", fontSize = 12.sp, color = TextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "• Heads-Up Call Notification Card", fontSize = 12.sp, color = TextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "• GitHub In-App OTA Auto-Update", fontSize = 12.sp, color = TextDark)
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
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

// ============================================================
// 6. CALLING COUNTRY & VOICEMAIL DIALOG
// ============================================================
@Composable
fun CallingCountryDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val countries = listOf(
        Pair("Bangladesh", "+880"),
        Pair("United States", "+1"),
        Pair("United Kingdom", "+44"),
        Pair("India", "+91"),
        Pair("Saudi Arabia", "+966"),
        Pair("United Arab Emirates", "+971"),
        Pair("Malaysia", "+60"),
        Pair("Singapore", "+65"),
        Pair("Canada", "+1"),
        Pair("Australia", "+61")
    )
    var selectedCode by remember { mutableStateOf(appSettings.callingCountryCode) }
    var selectedName by remember { mutableStateOf(appSettings.callingCountryName) }
    var voicemailInput by remember { mutableStateOf(appSettings.voicemailNumber) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DialogHeader(
                    title = "Country & Voicemail",
                    icon = Icons.Default.Language,
                    iconTint = PurplePrimary,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Default Country Code",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Used to format local phone numbers for dialing",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for ((name, code) in countries) {
                        val isSelected = (selectedCode == code && selectedName == name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedCode = code
                                    selectedName = name
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedCode = code
                                    selectedName = name
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$name ($code)",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Voicemail Number",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Operator voicemail shortcut (e.g. *121#)",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = voicemailInput,
                    onValueChange = { voicemailInput = it },
                    singleLine = true,
                    placeholder = { Text("*121#", color = TextLightGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        appSettings.callingCountryCode = selectedCode
                        appSettings.callingCountryName = selectedName
                        appSettings.voicemailNumber = voicemailInput.trim()
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 7. SPEED DIAL (KEYPAD 1-9) DIALOG
// ============================================================
@Composable
fun SpeedDialDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var speedDials by remember { mutableStateOf(appSettings.getAllSpeedDials()) }
    var editingDigit by remember { mutableStateOf<Int?>(null) }
    var editName by remember { mutableStateOf("") }
    var editNumber by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DialogHeader(
                    title = "Speed Dial (১-৯)",
                    icon = Icons.Default.Star,
                    iconTint = Color(0xFFF59E0B),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ডায়ালপ্যাডের কী লং-প্রেস করে সরাসরি কল করার জন্য নাম্বার সেট করুন:",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Digit 1 is Voicemail
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(PurpleUltraLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Voicemail", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
                                Text(appSettings.voicemailNumber, fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    }
                }

                // Digits 2 to 9
                for (digit in 2..9) {
                    val entry = speedDials[digit]
                    val isEditingThis = editingDigit == digit

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (entry != null) PurplePrimary.copy(alpha = 0.4f) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(if (entry != null) PurplePrimary else Color(0xFFCBD5E1), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("$digit", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry?.second ?: "Unassigned (ফাঁকা)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = if (entry != null) TextDark else TextLightGray
                                        )
                                        if (entry != null) {
                                            Text(entry.first, fontSize = 11.sp, color = TextMuted)
                                        }
                                    }
                                }

                                Row {
                                    if (entry != null) {
                                        IconButton(
                                            onClick = {
                                                appSettings.removeSpeedDial(digit)
                                                speedDials = appSettings.getAllSpeedDials()
                                                onSaved()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isEditingThis) {
                                                editingDigit = null
                                            } else {
                                                editingDigit = digit
                                                editName = entry?.second ?: ""
                                                editNumber = entry?.first ?: ""
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isEditingThis) Icons.Default.Close else Icons.Default.Add,
                                            contentDescription = "Set",
                                            tint = PurplePrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isEditingThis) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Name", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = editNumber,
                                    onValueChange = { editNumber = it },
                                    label = { Text("Phone Number", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        if (editNumber.isNotBlank()) {
                                            val nameToSave = if (editName.isBlank()) "Speed Dial $digit" else editName
                                            appSettings.setSpeedDial(digit, editNumber, nameToSave)
                                            speedDials = appSettings.getAllSpeedDials()
                                            editingDigit = null
                                            onSaved()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Assign to Key $digit", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 8. EMERGENCY SOS (999 BANGLADESH) DIALOG
// ============================================================
@Composable
fun EmergencySosDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onCallEmergency: (String) -> Unit
) {
    var customNumber by remember { mutableStateOf(appSettings.customEmergencyNumber ?: "") }
    var customName by remember { mutableStateOf(appSettings.customEmergencyName ?: "") }
    var isEditingCustom by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DialogHeader(
                    title = "Emergency SOS (জরুরি সেবা)",
                    icon = Icons.Default.Warning,
                    iconTint = Color(0xFFDC2626),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                // National Emergency 999 Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFDC2626), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("National Emergency (999)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF991B1B))
                                Text("Police • Fire Service • Ambulance", fontSize = 11.sp, color = Color(0xFFB91C1C))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onCallEmergency("999")
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Call 999 Now", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Other Helplines
                Text(
                    text = "Official National Helplines",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f).clickable {
                            onCallEmergency("109")
                            onDismiss()
                        }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("109", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PurplePrimary)
                            Text("Women & Child Help", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.weight(1f).clickable {
                            onCallEmergency("1090")
                            onDismiss()
                        }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("1090", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0284C7))
                            Text("Disaster Helpline", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Emergency Contact
                Text(
                    text = "Personal Emergency Contact",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (appSettings.customEmergencyNumber.isNullOrBlank() || isEditingCustom) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Contact Name (e.g. Mom)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customNumber,
                        onValueChange = { customNumber = it },
                        label = { Text("Emergency Phone Number", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (customNumber.isNotBlank()) {
                                appSettings.customEmergencyNumber = customNumber.trim()
                                appSettings.customEmergencyName = if (customName.isBlank()) "Emergency Contact" else customName.trim()
                                isEditingCustom = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Emergency Contact", fontSize = 13.sp)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(appSettings.customEmergencyName ?: "Emergency Contact", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                Text(appSettings.customEmergencyNumber ?: "", fontSize = 12.sp, color = TextMuted)
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        appSettings.customEmergencyNumber?.let { onCallEmergency(it) }
                                        onDismiss()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", tint = CallIncomingGreen, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { isEditingCustom = true }, modifier = Modifier.size(34.dp)) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 9. VCF CONTACTS IMPORT & EXPORT DIALOG
// ============================================================
@Composable
fun VcfExportImportDialog(
    contactsRepository: ContactsRepository,
    onDismiss: () -> Unit,
    onImportFinished: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            coroutineScope.launch {
                val res = VcfManager.importContactsFromVcf(context, uri)
                isLoading = false
                if (res.isSuccess) {
                    statusMessage = "Successfully imported ${res.getOrNull()} contacts!"
                    onImportFinished()
                } else {
                    statusMessage = "Import failed: ${res.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                DialogHeader(
                    title = "Contacts Backup (.vcf)",
                    icon = Icons.Default.Share,
                    iconTint = PurplePrimary,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Export Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Export Contacts (.vcf)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                Text("Backup all your contacts to a file or share", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                coroutineScope.launch {
                                    val all = contactsRepository.getContacts()
                                    val res = VcfManager.exportContactsToVcf(context, all)
                                    isLoading = false
                                    if (res.isSuccess) {
                                        res.getOrNull()?.let { file ->
                                            VcfManager.shareVcfFile(context, file)
                                        }
                                    } else {
                                        statusMessage = "Export failed"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Export & Share .vcf", fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Import Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Import Contacts (.vcf)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                Text("Restore contacts from a .vcf file on your phone", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                importLauncher.launch("text/x-vcard")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Select .vcf File to Import", fontSize = 13.sp)
                        }
                    }
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = msg, fontSize = 12.sp, color = PurplePrimary, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ============================================================
// 12. CALLER ID & TRUECALLER SETTINGS DIALOG
// ============================================================
@Composable
fun CallerIdSettingsDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val authService = remember { TruecallerAuthService(context) }

    var isEnabled by remember { mutableStateOf(appSettings.callerIdEnabled) }
    var token by remember { mutableStateOf(appSettings.truecallerAuthToken ?: "") }
    var endpoint by remember { mutableStateOf(appSettings.truecallerCustomEndpoint ?: "") }
    var connectedPhone by remember { mutableStateOf(appSettings.truecallerPhoneNumber ?: "") }
    var isConnected by remember { mutableStateOf(!appSettings.truecallerAuthToken.isNullOrBlank()) }

    // OTP Flow State
    var phoneInput by remember { mutableStateOf("") }
    var otpSession by remember { mutableStateOf<TruecallerOtpSession?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var authSuccess by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    // Test Search State
    var testNumber by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<CallerIdResult?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Caller ID & Truecaller",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Real-time caller name & spam lookup",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Real-Time Caller ID",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Identify unknown numbers when typing or searching",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PurplePrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auth Success Message
                authSuccess?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = Color(0xFF065F46),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Auth Error Message
                authError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = err,
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // CONNECTED STATE
                if (isConnected) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Truecaller Connected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF14532D)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFDCFCE7))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }

                            if (connectedPhone.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Account: $connectedPhone",
                                    fontSize = 12.sp,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Real-time caller name, profile photo, and community spam ratings are active on dialer and contacts search.",
                                fontSize = 11.sp,
                                color = Color(0xFF15803D),
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    authService.logout()
                                    token = ""
                                    connectedPhone = ""
                                    isConnected = false
                                    authSuccess = "Truecaller disconnected."
                                    authError = null
                                    onSaved()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFDC2626)
                                )
                            ) {
                                Text(
                                    text = "Disconnect / Log out",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    // NOT CONNECTED STATE - OTP LOGIN
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Truecaller Login (SMS OTP)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "আপনার মোবাইল নম্বর দিন। সরাসরি Truecaller থেকে SMS OTP আসবে এবং স্বয়ংক্রিয়ভাবে কানেক্ট হয়ে যাবে।",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val currentSession = otpSession
                            if (currentSession == null) {
                                // Step 1: Phone input
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = it },
                                    label = { Text("Mobile Number") },
                                    placeholder = { Text("017XXXXXXXX বা +88017...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedIndicatorColor = PurplePrimary,
                                        unfocusedIndicatorColor = Color(0xFFCBD5E1)
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (phoneInput.isNotBlank()) {
                                            isSendingOtp = true
                                            authError = null
                                            authSuccess = null
                                            scope.launch {
                                                val res = authService.sendOtp(phoneInput)
                                                if (res.isSuccess) {
                                                    otpSession = res.getOrNull()
                                                    authSuccess = "OTP sent to ${otpSession?.fullPhoneNumber}. Please check SMS."
                                                } else {
                                                    authError = res.exceptionOrNull()?.message ?: "Failed to send OTP"
                                                }
                                                isSendingOtp = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    enabled = !isSendingOtp && phoneInput.isNotBlank()
                                ) {
                                    if (isSendingOtp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sending OTP...", fontSize = 13.sp, color = Color.White)
                                    } else {
                                        Text("Send Login OTP (কোড পাঠান)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else {
                                // Step 2: OTP verification
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFEFF6FF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "OTP sent to: ${currentSession.fullPhoneNumber}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E40AF)
                                            )
                                            Text(
                                                text = "SMS ইনবক্সে আসা ৬ সংখ্যার কোড লিখুন",
                                                fontSize = 10.sp,
                                                color = Color(0xFF3B82F6)
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                otpSession = null
                                                otpInput = ""
                                                authError = null
                                            }
                                        ) {
                                            Text("Change", fontSize = 11.sp, color = PurplePrimary)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = otpInput,
                                    onValueChange = { if (it.length <= 8) otpInput = it },
                                    label = { Text("SMS OTP Code") },
                                    placeholder = { Text("e.g. 123456") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedIndicatorColor = PurplePrimary,
                                        unfocusedIndicatorColor = Color(0xFFCBD5E1)
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        if (otpInput.isNotBlank()) {
                                            isVerifyingOtp = true
                                            authError = null
                                            authSuccess = null
                                            scope.launch {
                                                val res = authService.verifyOtp(currentSession, otpInput)
                                                if (res.isSuccess) {
                                                    val loginRes = res.getOrNull()!!
                                                    token = loginRes.installationId
                                                    connectedPhone = currentSession.fullPhoneNumber
                                                    isConnected = true
                                                    otpSession = null
                                                    otpInput = ""
                                                    authSuccess = "Truecaller Connected successfully!"
                                                    onSaved()
                                                } else {
                                                    authError = res.exceptionOrNull()?.message ?: "OTP Verification failed"
                                                }
                                                isVerifyingOtp = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    enabled = !isVerifyingOtp && otpInput.isNotBlank()
                                ) {
                                    if (isVerifyingOtp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Verifying...", fontSize = 13.sp, color = Color.White)
                                    } else {
                                        Text("Verify & Connect (যাচাই করুন)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test Lookup Tool
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Test Number Lookup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "যেকোনো নম্বর লিখে রিয়েল ডাটা পরীক্ষা করুন",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = testNumber,
                                onValueChange = { testNumber = it },
                                placeholder = { Text("017... or 16247") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedIndicatorColor = PurplePrimary,
                                    unfocusedIndicatorColor = Color(0xFFCBD5E1)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (testNumber.isNotBlank()) {
                                        isTesting = true
                                        testResult = null
                                        appSettings.truecallerAuthToken = token.trim().ifBlank { null }
                                        appSettings.truecallerCustomEndpoint = endpoint.trim().ifBlank { null }
                                        scope.launch {
                                            val service = CallerIdService(context)
                                            val res = service.lookup(testNumber)
                                            testResult = res
                                            isTesting = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                enabled = !isTesting && testNumber.isNotBlank()
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text("Test", fontSize = 12.sp)
                                }
                            }
                        }

                        // Test Result Preview
                        testResult?.let { res ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (res.isSpam) Color(0xFFDC2626) else Color(0xFF16A34A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = res.name ?: "${res.carrier} User",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (res.isSpam) Color(0xFFDC2626) else TextDark
                                        )
                                    }
                                    Text(
                                        text = "${res.carrier} • ${res.location} • Source: ${res.source}",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    if (res.isSpam) {
                                        Text(
                                            text = "⚠️ ${res.spamCategory ?: "Spam Warning"}",
                                            fontSize = 10.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Advanced Settings Toggle (Manual Token / Proxy URL)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Advanced: Custom Token / Proxy Gateway",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }

                if (showAdvanced) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { 
                            token = it 
                            isConnected = it.isNotBlank()
                        },
                        label = { Text("Manual Auth Token") },
                        placeholder = { Text("Paste Truecaller Token") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        token = clip.trim()
                                        isConnected = token.isNotBlank()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedIndicatorColor = PurplePrimary,
                            unfocusedIndicatorColor = Color(0xFFE2E8F0)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Custom Gateway URL (Optional)") },
                        placeholder = { Text("https://my-proxy.com/api/lookup") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedIndicatorColor = PurplePrimary,
                            unfocusedIndicatorColor = Color(0xFFE2E8F0)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Save & Close Button
                Button(
                    onClick = {
                        appSettings.callerIdEnabled = isEnabled
                        appSettings.truecallerAuthToken = token.trim().ifBlank { null }
                        appSettings.truecallerCustomEndpoint = endpoint.trim().ifBlank { null }
                        if (connectedPhone.isNotBlank()) {
                            appSettings.truecallerPhoneNumber = connectedPhone
                        }
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done (সংরক্ষণ করুন)", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}


