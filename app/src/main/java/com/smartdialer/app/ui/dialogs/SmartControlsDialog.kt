package com.smartdialer.app.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.utils.BiometricLockHelper

@Composable
fun SmartControlsDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    var appLockEnabled by remember { mutableStateOf(appSettings.appLockEnabled) }
    var flipToSilence by remember { mutableStateOf(appSettings.flipToSilenceEnabled) }
    var flashAlert by remember { mutableStateOf(appSettings.flashAlertEnabled) }
    var callerAnnouncer by remember { mutableStateOf(appSettings.callerAnnouncerEnabled) }
    var postCallSummary by remember { mutableStateOf(appSettings.postCallSummaryEnabled) }

    val templates = remember {
        mutableStateListOf<String>().apply { addAll(appSettings.getQuickReplyTemplates()) }
    }
    var newTemplate by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                DialogHeader(
                    title = "Smart Controls & Lock",
                    icon = Icons.Default.Tune,
                    iconTint = theme.primary,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(14.dp))

                SmartToggleRow(
                    icon = Icons.Default.Fingerprint,
                    iconColor = Color(0xFF7C3AED),
                    title = "Biometric / PIN App Lock",
                    subtitle = "Require fingerprint or screen lock to open Smart Dialer",
                    checked = appLockEnabled,
                    onCheckedChange = { wantEnable ->
                        if (wantEnable && !BiometricLockHelper.isDeviceAuthenticationAvailable(context)) {
                            Toast.makeText(
                                context,
                                "Please enable a screen lock / fingerprint in device settings first",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            appLockEnabled = wantEnable
                        }
                    }
                )

                SmartToggleRow(
                    icon = Icons.Default.ScreenRotation,
                    iconColor = Color(0xFF0284C7),
                    title = "Flip-to-Silence Incoming",
                    subtitle = "Place phone face-down to silence incoming ringtone",
                    checked = flipToSilence,
                    onCheckedChange = { flipToSilence = it }
                )

                SmartToggleRow(
                    icon = Icons.Default.FlashlightOn,
                    iconColor = Color(0xFFF59E0B),
                    title = "Flashlight Call Alert",
                    subtitle = "Blink rear camera flashlight on incoming calls",
                    checked = flashAlert,
                    onCheckedChange = { flashAlert = it }
                )

                SmartToggleRow(
                    icon = Icons.Default.RecordVoiceOver,
                    iconColor = Color(0xFF10B981),
                    title = "Caller Name Announcer (TTS)",
                    subtitle = "Speak caller name aloud when incoming call rings",
                    checked = callerAnnouncer,
                    onCheckedChange = { callerAnnouncer = it }
                )

                SmartToggleRow(
                    icon = Icons.Default.Tune,
                    iconColor = Color(0xFFEC4899),
                    title = "Post-Call Summary Popup",
                    subtitle = "Show call notes, callback reminder & quick actions after calls",
                    checked = postCallSummary,
                    onCheckedChange = { postCallSummary = it }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = theme.divider)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Quick Reject Reply Templates",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                templates.forEachIndexed { idx, msg ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, theme.divider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = theme.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (templates.size > 1) {
                                IconButton(
                                    onClick = { templates.removeAt(idx) },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTemplate,
                        onValueChange = { newTemplate = it },
                        placeholder = { Text("Add custom reply...", fontSize = 12.sp, color = theme.textMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = theme.surface,
                            unfocusedContainerColor = theme.surface,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedIndicatorColor = theme.primary,
                            unfocusedIndicatorColor = theme.divider
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (newTemplate.isNotBlank()) {
                                templates.add(newTemplate.trim())
                                newTemplate = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = theme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        appSettings.appLockEnabled = appLockEnabled
                        appSettings.flipToSilenceEnabled = flipToSilence
                        appSettings.flashAlertEnabled = flashAlert
                        appSettings.callerAnnouncerEnabled = callerAnnouncer
                        appSettings.postCallSummaryEnabled = postCallSummary
                        appSettings.setQuickReplyTemplates(templates.toList())
                        onSaved()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) {
                    Text("Save Smart Settings", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SmartToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
                Text(text = subtitle, fontSize = 11.sp, color = theme.textMuted)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = theme.primary)
        )
    }
}
