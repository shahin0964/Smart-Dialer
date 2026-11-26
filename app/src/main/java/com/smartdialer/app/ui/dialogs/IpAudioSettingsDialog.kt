package com.smartdialer.app.ui.dialogs

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextMuted

@Composable
fun IpAudioSettingsDialog(
    ipPreferences: IpCallPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val view = LocalView.current
    val theme = LocalAppTheme.current

    var selectedInput by remember { mutableStateOf(ipPreferences.audioInputSource) }
    var selectedQuality by remember { mutableStateOf(ipPreferences.audioQualityPreset) }
    var selectedCodec by remember { mutableStateOf(ipPreferences.preferredCodec) }
    var enableAec by remember { mutableStateOf(ipPreferences.enableAec) }
    var enableNs by remember { mutableStateOf(ipPreferences.enableNs) }
    var enableAgc by remember { mutableStateOf(ipPreferences.enableAgc) }
    var headsetOnlyMode by remember { mutableStateOf(ipPreferences.headsetOnlyMode) }
    var selectedMicBoost by remember { mutableIntStateOf(ipPreferences.micGainBoost) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(theme.primaryUltraLight, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SurroundSound,
                        contentDescription = "Audio Settings",
                        tint = theme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "IP Call Sound & Audio Quality",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                Text(
                    text = "High-definition sound input & crystal voice tuning",
                    fontSize = 12.sp,
                    color = theme.textMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                // 1. Sound Input Source (Microphone)
                AudioSectionTitle(icon = Icons.Default.Mic, title = "Sound Input Source")
                val inputOptions = listOf(
                    "VOICE_COMMUNICATION" to "Voice Communication (VoIP Tuned)",
                    "MIC" to "Standard Microphone (Direct)",
                    "VOICE_RECOGNITION" to "Voice Recognition (DSP Filtered)",
                    "CAMCORDER" to "Directional Camcorder Mic"
                )
                inputOptions.forEach { (value, label) ->
                    AudioRadioOption(
                        selected = selectedInput == value,
                        label = label,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedInput = value
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Sound Quality Presets (720p HD -> 4K Ultra HD)
                AudioSectionTitle(icon = Icons.Default.GraphicEq, title = "Audio Quality & Bandwidth")
                val qualityOptions = listOf(
                    "STANDARD_8K" to ("Standard 8 kHz" to "Low data consumption, 2G/3G compatible"),
                    "HD_16K" to ("720p HD Voice (16 kHz)" to "Crisp clarity & wideband speech (Recommended)"),
                    "FHD_24K" to ("1080p FHD Voice (24 kHz)" to "Super-wideband with rich vocal tone"),
                    "ULTRA_48K" to ("4K Ultra HD (48 kHz)" to "Studio Hi-Fi fullband capture & fidelity")
                )
                qualityOptions.forEach { (value, pair) ->
                    val (title, subtitle) = pair
                    AudioQualityOption(
                        selected = selectedQuality == value,
                        title = title,
                        subtitle = subtitle,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedQuality = value
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Audio Codec Preference
                AudioSectionTitle(icon = Icons.Default.Speed, title = "VoIP Audio Codec")
                val codecOptions = listOf(
                    "AUTO" to "Auto Negotiation (Adaptive)",
                    "PCMA" to "PCMA / G.711 A-law (64 kbps, BD/EU)",
                    "PCMU" to "PCMU / G.711 µ-law (64 kbps, US)"
                )
                codecOptions.forEach { (value, label) ->
                    AudioRadioOption(
                        selected = selectedCodec == value,
                        label = label,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedCodec = value
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Hardware Noise & Echo Processing
                AudioSectionTitle(icon = Icons.Default.RecordVoiceOver, title = "Hardware Voice Processing")

                AudioToggleItem(
                    label = "Acoustic Echo Cancellation (AEC)",
                    subtitle = "Prevents speaker feedback & echo loop",
                    checked = enableAec,
                    onCheckedChange = { enableAec = it }
                )

                AudioToggleItem(
                    label = "Hardware Noise Suppression (NS)",
                    subtitle = "Filters out fan, AC & background noise",
                    checked = enableNs,
                    onCheckedChange = { enableNs = it }
                )

                AudioToggleItem(
                    label = "Automatic Gain Control (AGC)",
                    subtitle = "Maintains consistent mic speaking level",
                    checked = enableAgc,
                    onCheckedChange = { enableAgc = it }
                )

                AudioToggleItem(
                    label = "Headset-Only Mode (Smart Routing)",
                    subtitle = "Locks call audio to Bluetooth / USB-C / Wired headset when connected",
                    checked = headsetOnlyMode,
                    onCheckedChange = { headsetOnlyMode = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Digital Mic Gain Boost
                Text(
                    text = "Microphone Gain Boost",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.textPrimary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val boosts = listOf(0 to "Normal (0dB)", 3 to "+3 dB", 6 to "+6 dB", 12 to "+12 dB")
                    boosts.forEach { (gain, label) ->
                        val isSelected = selectedMicBoost == gain
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) theme.primary else theme.surface)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    selectedMicBoost = gain
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else theme.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surface)
                    ) {
                        Text("Cancel", color = theme.textPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            ipPreferences.audioInputSource = selectedInput
                            ipPreferences.audioQualityPreset = selectedQuality
                            ipPreferences.preferredCodec = selectedCodec
                            ipPreferences.enableAec = enableAec
                            ipPreferences.enableNs = enableNs
                            ipPreferences.enableAgc = enableAgc
                            ipPreferences.headsetOnlyMode = headsetOnlyMode
                            ipPreferences.micGainBoost = selectedMicBoost
                            onSaved()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = theme.primary
        )
    }
}

@Composable
private fun AudioRadioOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.primaryUltraLight else theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) theme.primary else theme.divider, CircleShape)
                .background(if (selected) theme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) theme.textPrimary else theme.textMuted
        )
    }
}

@Composable
private fun AudioQualityOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) theme.primary else theme.divider,
                shape = RoundedCornerShape(10.dp)
            )
            .background(if (selected) theme.primaryUltraLight else theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) theme.primary else theme.divider, CircleShape)
                .background(if (selected) theme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = theme.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = theme.textMuted
            )
        }
    }
}

@Composable
private fun AudioToggleItem(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = theme.textMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.primary
            )
        )
    }
}
