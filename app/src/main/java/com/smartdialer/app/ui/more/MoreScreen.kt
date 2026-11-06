package com.smartdialer.app.ui.more

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.app.data.model.SimInfo
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.data.repository.OtaUpdateManager
import com.smartdialer.app.data.repository.SimRepository
import com.smartdialer.app.ui.components.IpCallConfigDialog
import com.smartdialer.app.ui.dialogs.AboutSmartDialerDialog
import com.smartdialer.app.ui.dialogs.AppUpdateDialog
import com.smartdialer.app.ui.dialogs.BlockedNumbersDialog
import com.smartdialer.app.ui.dialogs.CallRecordingDialog
import com.smartdialer.app.ui.dialogs.DualSimSettingsDialog
import com.smartdialer.app.ui.dialogs.SoundsHapticsDialog
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted

@Composable
fun MoreScreen(
    ipPreferences: IpCallPreferences,
    simRepository: SimRepository,
    appSettings: AppSettingsPreferences,
    onRequestDefaultDialer: () -> Unit
) {
    val context = LocalContext.current
    var isDefaultDialer by remember { mutableStateOf(false) }

    var showIpDialog by remember { mutableStateOf(false) }
    var showDualSimDialog by remember { mutableStateOf(false) }
    var showCallRecordDialog by remember { mutableStateOf(false) }
    var showSoundsHapticsDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var refreshKey by remember { mutableStateOf(0) }
    var sims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }

    fun checkDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            isDefaultDialer = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    LaunchedEffect(Unit) {
        checkDefaultDialer()
    }

    LaunchedEffect(refreshKey) {
        checkDefaultDialer()
        sims = simRepository.getSimCards()
    }

    // Dialogs
    if (showUpdateDialog) {
        AppUpdateDialog(
            onDismiss = { showUpdateDialog = false }
        )
    }

    if (showIpDialog) {
        IpCallConfigDialog(
            ipPreferences = ipPreferences,
            onDismiss = { showIpDialog = false },
            onSaved = { refreshKey++ }
        )
    }

    if (showDualSimDialog) {
        DualSimSettingsDialog(
            sims = sims,
            appSettings = appSettings,
            onDismiss = { showDualSimDialog = false },
            onSaved = { refreshKey++ }
        )
    }

    if (showCallRecordDialog) {
        CallRecordingDialog(
            appSettings = appSettings,
            onDismiss = { showCallRecordDialog = false },
            onSaved = { refreshKey++ }
        )
    }

    if (showSoundsHapticsDialog) {
        SoundsHapticsDialog(
            appSettings = appSettings,
            onDismiss = { showSoundsHapticsDialog = false },
            onSaved = { refreshKey++ }
        )
    }

    if (showBlockedDialog) {
        BlockedNumbersDialog(
            appSettings = appSettings,
            onDismiss = { showBlockedDialog = false },
            onSaved = { refreshKey++ }
        )
    }

    if (showAboutDialog) {
        AboutSmartDialerDialog(
            onDismiss = { showAboutDialog = false },
            onCheckUpdates = { showUpdateDialog = true }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings & Features",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Default Dialer Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PurpleUltraLight)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurplePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneInTalk,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Default Phone App",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = if (isDefaultDialer) "Smart Dialer is your default dialer" else "Not set as default phone app",
                                fontSize = 12.sp,
                                color = if (isDefaultDialer) CallIncomingGreen else TextMuted
                            )
                        }
                    }
                }

                if (!isDefaultDialer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onRequestDefaultDialer()
                            checkDefaultDialer()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set Smart Dialer as Default")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Options List
        // 1. IP Calling & IP Number Settings
        val ipSummary = remember(refreshKey, ipPreferences.balance) {
            if (ipPreferences.username.isNotBlank()) {
                "Account: ${ipPreferences.username} • Balance: ৳${String.format(java.util.Locale.US, "%.2f", ipPreferences.balance)}"
            } else if (ipPreferences.domain.isNotBlank()) {
                "Domain: ${ipPreferences.domain} • Setup SIP Account & Dashboard"
            } else {
                "Setup SIP Account & Dashboard"
            }
        }

        MoreOptionItem(
            icon = Icons.Default.Language,
            title = "IP Calling & IP Number Settings",
            subtitle = ipSummary,
            onClick = { showIpDialog = true }
        )

        // 2. Dual SIM Settings
        val simSummary = remember(refreshKey, appSettings.preferredSimSlot) {
            if (appSettings.preferredSimSlot == -1) {
                "Outgoing: Always Ask"
            } else {
                "Outgoing: SIM ${appSettings.preferredSimSlot + 1}"
            }
        }

        MoreOptionItem(
            icon = Icons.Default.SimCard,
            title = "Dual SIM Settings",
            subtitle = simSummary,
            onClick = { showDualSimDialog = true }
        )

        // 3. Call Recording
        val recordSummary = remember(refreshKey, appSettings.autoRecord) {
            if (appSettings.autoRecord) {
                "Auto Recording: Active (${appSettings.audioFormat})"
            } else {
                "Record incoming and outgoing calls"
            }
        }

        MoreOptionItem(
            icon = Icons.Default.RecordVoiceOver,
            title = "Call Recording",
            subtitle = recordSummary,
            onClick = { showCallRecordDialog = true }
        )

        // 4. Sounds & Haptics
        val soundSummary = remember(refreshKey, appSettings.dialpadTones, appSettings.dialpadVibration) {
            "Tones: ${if (appSettings.dialpadTones) "On" else "Off"}, Haptics: ${if (appSettings.dialpadVibration) "On" else "Off"}"
        }

        MoreOptionItem(
            icon = Icons.Default.VolumeUp,
            title = "Sounds & Haptics",
            subtitle = soundSummary,
            onClick = { showSoundsHapticsDialog = true }
        )

        // 5. Blocked Numbers & Spam Protection
        val blockedSummary = remember(refreshKey, appSettings.blockedNumbers.size, appSettings.spamProtectionEnabled, appSettings.spamProtectionLevel) {
            if (!appSettings.spamProtectionEnabled) {
                if (appSettings.blockedNumbers.isNotEmpty()) {
                    "${appSettings.blockedNumbers.size} numbers blocked (Spam filter Off)"
                } else {
                    "Protection disabled"
                }
            } else {
                "${appSettings.spamProtectionLevel.title} (Layer ${appSettings.spamProtectionLevel.layerNumber}) • ${appSettings.blockedNumbers.size} blocked"
            }
        }

        MoreOptionItem(
            icon = Icons.Default.Security,
            title = "3-Layer Spam & Block Protection",
            subtitle = blockedSummary,
            onClick = { showBlockedDialog = true }
        )

        // 6. App OTA Update
        val currentVersion = remember { OtaUpdateManager.getCurrentVersion(context) }
        MoreOptionItem(
            icon = Icons.Default.SystemUpdate,
            title = "App OTA Update",
            subtitle = "Version v$currentVersion • Check GitHub updates & changelog",
            onClick = { showUpdateDialog = true }
        )

        // 7. About Smart Dialer
        MoreOptionItem(
            icon = Icons.Default.Info,
            title = "About Smart Dialer",
            subtitle = "Smart Dialer v$currentVersion (Official Build)",
            onClick = { showAboutDialog = true }
        )
    }
}

@Composable
fun MoreOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PurpleUltraLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextLightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
