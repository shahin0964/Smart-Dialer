package com.smartdialer.app.ui.call

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.ui.components.ContactAvatar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.app.data.model.ActiveCallInfo
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.theme.CallBgDark1
import com.smartdialer.app.ui.theme.CallBgDark2
import com.smartdialer.app.ui.theme.CallBgDark3
import com.smartdialer.app.ui.theme.CallButtonActive
import com.smartdialer.app.ui.theme.CallButtonTranslucent
import com.smartdialer.app.ui.theme.CallEndRed
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.PurpleLight
import com.smartdialer.app.ui.theme.PurplePrimary

@Composable
fun OngoingCallScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val callInfo by CallManager.currentCallState.collectAsState()
    var showDtmfKeypad by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CallBgDark1, CallBgDark2, CallBgDark3)
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Logo + "Smart Dialer" | Info Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Smart Dialer",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { /* Info */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Call Info",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Center: Glowing Caller Avatar + Name + Number + Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Neon Glowing Avatar (Red if spam, Purple/Blue if safe)
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .border(
                            width = 4.dp,
                            brush = if (callInfo.isSpam) {
                                Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                            } else {
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF8B5CF6),
                                        Color(0xFF3B82F6),
                                        Color(0xFF8B5CF6)
                                    )
                                )
                            },
                            shape = CircleShape
                        )
                        .background(if (callInfo.isSpam) Color(0xFF450A0A) else Color(0xFF23204E)),
                    contentAlignment = Alignment.Center
                ) {
                    ContactAvatar(
                        name = if (callInfo.contactName.isNotBlank()) callInfo.contactName else callInfo.number,
                        photoUri = callInfo.photoUri,
                        size = 122.dp,
                        preferHighRes = true,
                        fontSize = 44.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Spam / Blocked Protection Banner
                if (callInfo.isSpam) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF7F1D1D).copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (callInfo.isBlocked) Icons.Default.Block else Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    callInfo.isBlocked -> "🚫 BLOCKED CALLER"
                                    callInfo.isSilencedSpam -> "🔕 SILENCED SPAM (${callInfo.spamCategory ?: "Telemarketing"})"
                                    else -> "⚠️ SUSPECTED SPAM: ${callInfo.spamCategory ?: "Scam / Fraud"}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Caller Name
                Text(
                    text = if (callInfo.contactName.isNotBlank()) callInfo.contactName else "Caller",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Caller Number
                if (callInfo.number.isNotBlank()) {
                    Text(
                        text = callInfo.number,
                        fontSize = 16.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Call Status & Timer with Signal bars icon
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Signal indicator bars
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(modifier = Modifier.width(3.dp).height(6.dp).background(CallIncomingGreen, RoundedCornerShape(1.dp)))
                        Box(modifier = Modifier.width(3.dp).height(10.dp).background(CallIncomingGreen, RoundedCornerShape(1.dp)))
                        Box(modifier = Modifier.width(3.dp).height(14.dp).background(CallIncomingGreen, RoundedCornerShape(1.dp)))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = when (callInfo.status) {
                            CallStatus.RINGING -> "Incoming Call..."
                            CallStatus.DIALING -> "Dialing..."
                            CallStatus.CONNECTING -> "Connecting..."
                            CallStatus.HOLDING -> "On Hold"
                            CallStatus.DISCONNECTED -> "Call Ended"
                            else -> callInfo.durationFormatted
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // In-Call Controls or Incoming Call Controls
            if (callInfo.status == CallStatus.RINGING) {
                // Incoming Call: Answer & Reject Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reject (Red)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CallEndRed)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    CallManager.reject()
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Decline", color = Color.White, fontSize = 14.sp)
                    }

                    // Answer (Green)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CallIncomingGreen)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    CallManager.answer()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Answer",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Answer", color = Color.White, fontSize = 14.sp)
                    }
                }
            } else {
                // Active / Dialing Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Row 1: Mute, Keypad, Speaker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallCircleButton(
                            icon = if (callInfo.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (callInfo.isMuted) "Unmute" else "Mute",
                            isActive = callInfo.isMuted,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                CallManager.toggleMute()
                            }
                        )

                        InCallCircleButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = showDtmfKeypad,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showDtmfKeypad = !showDtmfKeypad
                            }
                        )

                        InCallCircleButton(
                            icon = Icons.Default.VolumeUp,
                            label = "Speaker",
                            isActive = callInfo.isSpeakerOn,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                CallManager.toggleSpeaker(context)
                            }
                        )
                    }

                    // Row 2: Add Call, Record Call
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallCircleButton(
                            icon = Icons.Default.Add,
                            label = "Add Call",
                            isActive = false,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                // Add Call
                            }
                        )

                        InCallRecordButton(
                            isRecording = callInfo.isRecording,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                CallManager.toggleRecording()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // End Call Button (Large Red Circular with drop shadow)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(elevation = 12.dp, shape = CircleShape, spotColor = CallEndRed)
                            .clip(CircleShape)
                            .background(CallEndRed)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                CallManager.disconnect()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        // DTMF Keypad Overlay if opened
        if (showDtmfKeypad) {
            DtmfOverlay(
                onDigit = { digit ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    CallManager.sendDtmf(digit)
                },
                onClose = { showDtmfKeypad = false }
            )
        }
    }
}

@Composable
fun InCallCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (isActive) CallButtonActive else CallButtonTranslucent)
                .border(
                    width = 1.dp,
                    color = if (isActive) PurpleLight else Color(0x33FFFFFF),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun InCallRecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(CallButtonTranslucent)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(listOf(PurpleLight, Color(0xFFE11D48), PurpleLight)),
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = "Record Call",
                tint = if (isRecording) Color(0xFFEF4444) else Color(0xFFF43F5E),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isRecording) "Recording..." else "Record Call",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DtmfOverlay(
    onDigit: (Char) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60F0C29))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1B1B3A),
            modifier = Modifier
                .padding(24.dp)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DTMF Keypad",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                val digits = listOf(
                    listOf('1', '2', '3'),
                    listOf('4', '5', '6'),
                    listOf('7', '8', '9'),
                    listOf('*', '0', '#')
                )

                for (row in digits) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        for (char in row) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CallButtonTranslucent)
                                    .clickable { onDigit(char) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char.toString(),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Close",
                    color = PurpleLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(8.dp)
                )
            }
        }
    }
}
