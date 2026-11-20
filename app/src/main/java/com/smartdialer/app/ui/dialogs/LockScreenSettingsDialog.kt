package com.smartdialer.app.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
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
    var canDrawOverlays by remember {
        mutableStateOf(OemPermissionHelper.canDrawOverlays(context))
    }
    var showDeviceOptimizationPopup by remember {
        mutableStateOf(false)
    }

    // Refresh state when dialog opens
    LaunchedEffect(Unit) {
        isIgnoringBattery = OemPermissionHelper.isIgnoringBatteryOptimizations(context)
        canFullScreen = OemPermissionHelper.canUseFullScreenIntent(context)
        canDrawOverlays = OemPermissionHelper.canDrawOverlays(context)
    }

    val oemName = remember { OemPermissionHelper.getDeviceBrandName() }

    // Child Popup Dialog for Device Optimizations
    if (showDeviceOptimizationPopup) {
        DeviceOptimizationPopup(
            oemName = oemName,
            canDrawOverlays = canDrawOverlays,
            onDismiss = {
                showDeviceOptimizationPopup = false
                canDrawOverlays = OemPermissionHelper.canDrawOverlays(context)
            },
            onAllowOverlays = {
                OemPermissionHelper.openOverlaySettings(context)
                showDeviceOptimizationPopup = false
            },
            onAllowAutostart = {
                Toast.makeText(context, "Please enable Autostart for Smart-Dialer", Toast.LENGTH_LONG).show()
                OemPermissionHelper.openOemAutostartSettings(context)
                showDeviceOptimizationPopup = false
            },
            onAllowNotifications = {
                OemPermissionHelper.openAppNotificationSettings(context)
                showDeviceOptimizationPopup = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .statusBarsPadding()
                .navigationBarsPadding()
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

                    // Section 3: Device Optimizations & Pop-up Permission (Allow via Popup)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeviceOptimizationPopup = true }
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

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (canDrawOverlays) CallIncomingGreen.copy(alpha = 0.15f)
                                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (canDrawOverlays) "Pop-up Allowed" else "Action Needed",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (canDrawOverlays) CallIncomingGreen else Color(0xFFF59E0B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "On $oemName, ensure Autostart and 'Show on Lock Screen / Pop-up Window' are enabled so incoming calls are not blocked.",
                                fontSize = 12.sp,
                                color = theme.textMuted
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Primary Action: Open Popup to Allow Permissions
                            Button(
                                onClick = { showDeviceOptimizationPopup = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canDrawOverlays) theme.primary else Color(0xFFEA580C)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Allow via Popup (অনুমতি দিন)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        Toast.makeText(context, "Please enable Autostart for Smart-Dialer", Toast.LENGTH_SHORT).show()
                                        OemPermissionHelper.openOemAutostartSettings(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("1. Autostart", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        OemPermissionHelper.openOverlaySettings(context)
                                        canDrawOverlays = OemPermissionHelper.canDrawOverlays(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("2. Pop-up Window", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        OemPermissionHelper.openAppNotificationSettings(context)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("3. Banners", fontSize = 12.sp)
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

/**
 * Interactive Popup Dialog for Device Optimizations (Autostart, Floating Pop-up Window, and Banners).
 */
@Composable
fun DeviceOptimizationPopup(
    oemName: String,
    canDrawOverlays: Boolean,
    onDismiss: () -> Unit,
    onAllowOverlays: () -> Unit,
    onAllowAutostart: () -> Unit,
    onAllowNotifications: () -> Unit
) {
    val theme = LocalAppTheme.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .statusBarsPadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            color = theme.surface,
            tonalElevation = 10.dp
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Device Optimizations",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = oemName,
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
                HorizontalDivider(color = theme.divider)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Allow the following settings so $oemName reliably alerts you when a call comes in:",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Step 1: Pop-up Window
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = if (canDrawOverlays) CallIncomingGreen else Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1. Pop-up Window (Appear on Top)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary
                                )
                            }
                            Text(
                                text = if (canDrawOverlays) "Allowed" else "Action Needed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canDrawOverlays) CallIncomingGreen else Color(0xFFF59E0B)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Allows calls to display as a floating banner over other active apps.",
                            fontSize = 12.sp,
                            color = theme.textMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onAllowOverlays,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canDrawOverlays) theme.primary.copy(alpha = 0.7f) else theme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (canDrawOverlays) "Pop-up Window Already Allowed" else "Allow Pop-up Window (Allow/Deny)",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Step 2: Autostart
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "2. Autostart / Background Activity",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = theme.textPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Prevents $oemName from sleeping or closing Smart-Dialer in background.",
                            fontSize = 12.sp,
                            color = theme.textMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onAllowAutostart,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Autostart & Background Settings", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Step 3: Notification Banners
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3. Notification Banners & Sounds",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensures incoming calls trigger high priority ringtone, vibration, and banner.",
                            fontSize = 12.sp,
                            color = theme.textMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onAllowNotifications,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Configure Banners & Sounds", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Action Buttons
                Button(
                    onClick = {
                        if (!canDrawOverlays) {
                            onAllowOverlays()
                        } else {
                            onAllowAutostart()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Permissions (অনুমতি দিন)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel / বাতিল", color = theme.textMuted)
                }
            }
        }
    }
}
