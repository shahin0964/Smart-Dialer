package com.smartdialer.app.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.util.OemPermissionHelper

@Composable
fun LockScreenSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    var isIgnoringBattery by remember {
        mutableStateOf(OemPermissionHelper.isIgnoringBatteryOptimizations(context))
    }
    var canFullScreen by remember {
        mutableStateOf(OemPermissionHelper.canUseFullScreenIntent(context))
    }

    // Refresh state when dialog opens
    LaunchedEffect(Unit) {
        isIgnoringBattery = OemPermissionHelper.isIgnoringBatteryOptimizations(context)
        canFullScreen = OemPermissionHelper.canUseFullScreenIntent(context)
    }

    val isAggressiveOem = remember { OemPermissionHelper.isAggressiveOem() }
    val oemName = remember {
        when {
            OemPermissionHelper.isXiaomi() -> "Xiaomi / Redmi / Poco"
            OemPermissionHelper.isOppoOrRealme() -> "Oppo / Realme / OnePlus"
            OemPermissionHelper.isVivo() -> "Vivo / iQOO"
            OemPermissionHelper.isHuawei() -> "Huawei / Honor"
            else -> "Android Device"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = theme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEA580C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Lock Screen & Alerts",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Incoming call display & background wake",
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

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = theme.divider)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Explanatory card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "How incoming calls work in Smart-Dialer:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = theme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Phone Unlocked: Displays a floating banner (Heads-Up notification) so it doesn't interrupt your active app.\n• Phone Locked: Instantly turns on the screen and presents the full-size call screen with Answer/Decline buttons.",
                                fontSize = 12.sp,
                                color = theme.textMuted,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // Section 1: Unrestricted Battery
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryAlert,
                                        contentDescription = null,
                                        tint = if (isIgnoringBattery) CallIncomingGreen else Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Background Battery",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = theme.textPrimary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isIgnoringBattery) CallIncomingGreen.copy(alpha = 0.15f)
                                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isIgnoringBattery) "Unrestricted" else "Restricted",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIgnoringBattery) CallIncomingGreen else Color(0xFFF59E0B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isIgnoringBattery) {
                                    "Battery optimizations are disabled. Smart-Dialer will reliably receive incoming calls in deep sleep."
                                } else {
                                    "Your device may kill Smart-Dialer in deep sleep. Allow 'Unrestricted' background battery to guarantee calls ring on time."
                                },
                                fontSize = 12.sp,
                                color = theme.textMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    OemPermissionHelper.requestIgnoreBatteryOptimizations(context)
                                    isIgnoringBattery = OemPermissionHelper.isIgnoringBatteryOptimizations(context)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isIgnoringBattery) theme.primary.copy(alpha = 0.8f) else Color(0xFFEA580C)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (isIgnoringBattery) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isIgnoringBattery) "Battery Already Unrestricted" else "Request Whitelist (Allow / Deny Popup)",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Section 2: Full Screen Intent (Android 14+)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = null,
                                        tint = if (canFullScreen) CallIncomingGreen else Color(0xFFF59E0B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Full-Screen Calling",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = theme.textPrimary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (canFullScreen) CallIncomingGreen.copy(alpha = 0.15f)
                                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (canFullScreen) "Allowed" else "Action Needed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (canFullScreen) CallIncomingGreen else Color(0xFFF59E0B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Enables Smart-Dialer to turn on the screen and show the full call UI when your phone is locked or asleep.",
                                fontSize = 12.sp,
                                color = theme.textMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    OemPermissionHelper.openFullScreenIntentSettings(context)
                                    canFullScreen = OemPermissionHelper.canUseFullScreenIntent(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Configure Full Screen Intent Settings", fontSize = 13.sp)
                            }
                        }
                    }

                    // Section 3: OEM Specific Permissions (Xiaomi / Realme / Oppo / Vivo)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Device Optimizations ($oemName)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = theme.textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "On $oemName, ensure Autostart and 'Show on Lock Screen / Pop-up Window' are enabled so incoming calls are not blocked.",
                                fontSize = 12.sp,
                                color = theme.textMuted
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Please enable Autostart for Smart-Dialer", Toast.LENGTH_SHORT).show()
                                        OemPermissionHelper.openOemAutostartSettings(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("1. Open Autostart Settings", fontSize = 13.sp)
                                }

                                if (OemPermissionHelper.isXiaomi() || OemPermissionHelper.isOppoOrRealme()) {
                                    OutlinedButton(
                                        onClick = {
                                            Toast.makeText(context, "Enable 'Show on Lock screen' and 'Display pop-up windows'", Toast.LENGTH_LONG).show()
                                            OemPermissionHelper.openOemBackgroundPopupSettings(context)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("2. Enable Pop-up & Lock Screen Window", fontSize = 13.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        OemPermissionHelper.openAppNotificationSettings(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("3. Notification Banners & Sounds", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = theme.divider)
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
