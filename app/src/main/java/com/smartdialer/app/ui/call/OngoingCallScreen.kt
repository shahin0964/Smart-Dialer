package com.smartdialer.app.ui.call

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.draw.rotate
import com.smartdialer.app.MainActivity
import com.smartdialer.app.ui.theme.AppThemeRegistry
import com.smartdialer.app.utils.NetworkTypeHelper
import com.smartdialer.app.utils.SmsHelper
import com.smartdialer.app.utils.WhatsAppHelper
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.smartdialer.app.telecom.FloatingCallHeadManager
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
    var showQuickRejectSheet by remember { mutableStateOf(false) }

    val currentTheme by AppSettingsPreferences.themeFlow.collectAsState()
    val themePalette = AppThemeRegistry.getPalette(currentTheme)

    val infiniteTransition = rememberInfiniteTransition(label = "CallerScreenAnimation")

    val pulseSlow by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseSlow"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val rotatingAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing)),
        label = "rotatingAngle"
    )
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, delayMillis = 1100, easing = LinearEasing)),
        label = "wave2"
    )
    val auroraShift by infiniteTransition.animateFloat(
        initialValue = -120f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(tween(5500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auroraShift"
    )
    val heartbeatScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1500
                1f at 0
                1.10f at 220
                1.03f at 400
                1.12f at 600
                1f at 900
                1f at 1500
            },
            RepeatMode.Restart
        ),
        label = "heartbeat"
    )
    val eq1 by infiniteTransition.animateFloat(4f, 18f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq1")
    val eq2 by infiniteTransition.animateFloat(8f, 24f, infiniteRepeatable(tween(330, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq2")
    val eq3 by infiniteTransition.animateFloat(2f, 16f, infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq3")
    val eq4 by infiniteTransition.animateFloat(6f, 22f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq4")
    val eq5 by infiniteTransition.animateFloat(3f, 14f, infiniteRepeatable(tween(460, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq5")

    val bgBrush = if (currentTheme == "EMERALD_AURORA") {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF021B1A), Color(0xFF042F2E), Color(0xFF064E3B), Color(0xFF021B1A)),
            startY = auroraShift,
            endY = auroraShift + 1400f
        )
    } else {
        Brush.verticalGradient(themePalette.callerScreenGradient)
    }

    val buttonBgColor = when (currentTheme) {
        "IOS_GLASS" -> Color(0x38FFFFFF)
        "CYBERPUNK_AMOLED" -> Color(0xFF141428)
        else -> themePalette.callerButtonBg
    }

    val buttonActiveColor = themePalette.callerAccent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    context.startActivity(intent)
                                    Toast.makeText(context, "Please allow 'Display over other apps' to use Floating Bubble", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                FloatingCallHeadManager.show(context)
                                (context as? Activity)?.moveTaskToBack(true)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize to Floating Bubble",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(26.dp)
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
            }

            // Center: Glowing Animated Caller Avatar + Name + Number + Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated Theme-Specific Avatar Skin
                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (currentTheme) {
                        "IOS_GLASS" -> {
                            // Frosted breathing glow ring
                            Box(
                                modifier = Modifier
                                    .size((136 * pulseSlow).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0A84FF).copy(alpha = pulseAlpha * 0.35f))
                            )
                            Box(
                                modifier = Modifier
                                    .size((154 * pulseSlow).dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = pulseAlpha * 0.45f), CircleShape)
                            )
                        }
                        "SAMSUNG_ONE_UI" -> {
                            // One UI Signature Concentric Wave 1
                            Box(
                                modifier = Modifier
                                    .size((126 + wave1 * 60).dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF38BDF8).copy(alpha = (1f - wave1) * 0.7f), CircleShape)
                            )
                            // One UI Signature Concentric Wave 2
                            Box(
                                modifier = Modifier
                                    .size((126 + wave2 * 60).dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF38BDF8).copy(alpha = (1f - wave2) * 0.7f), CircleShape)
                            )
                        }
                        "CYBERPUNK_AMOLED" -> {
                            // Rotating neon sweep gradient ring
                            Box(
                                modifier = Modifier
                                    .size(148.dp)
                                    .rotate(rotatingAngle)
                                    .clip(CircleShape)
                                    .border(
                                        width = 3.5.dp,
                                        brush = Brush.sweepGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF007F), Color(0xFF00F0FF))),
                                        shape = CircleShape
                                    )
                            )
                            // Pulsing cyan glow behind
                            Box(
                                modifier = Modifier
                                    .size((134 * pulseSlow).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F0FF).copy(alpha = pulseAlpha * 0.25f))
                            )
                        }
                        "EMERALD_AURORA" -> {
                            // Aurora breathing radial aura
                            Box(
                                modifier = Modifier
                                    .size((142 * pulseSlow).dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF10B981).copy(alpha = pulseAlpha * 0.5f), Color.Transparent)
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size((154 * pulseSlow).dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFF34D399).copy(alpha = pulseAlpha * 0.55f), CircleShape)
                            )
                        }
                        "SUNSET_VELVET" -> {
                            // Velvet Heartbeat double-pulsing radiant aura
                            Box(
                                modifier = Modifier
                                    .size((138 * heartbeatScale).dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFFE11D48).copy(alpha = 0.45f), Color(0xFF3B0764).copy(alpha = 0.2f), Color.Transparent)
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size((152 * heartbeatScale).dp)
                                    .clip(CircleShape)
                                    .border(2.dp, Color(0xFFFDA4AF).copy(alpha = 0.55f), CircleShape)
                            )
                        }
                        else -> {
                            // Classic Dark / Sonar Radar Pulse
                            Box(
                                modifier = Modifier
                                    .size((128 + wave1 * 56).dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, themePalette.callerAccent.copy(alpha = (1f - wave1) * 0.65f), CircleShape)
                            )
                        }
                    }

                    // Avatar Core
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.5.dp,
                                brush = if (callInfo.isSpam) {
                                    Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                                } else {
                                    Brush.linearGradient(listOf(themePalette.callerAccent, themePalette.primaryLight))
                                },
                                shape = CircleShape
                            )
                            .background(if (callInfo.isSpam) Color(0xFF450A0A) else themePalette.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        ContactAvatar(
                            name = if (callInfo.contactName.isNotBlank()) callInfo.contactName else callInfo.number,
                            photoUri = callInfo.photoUri,
                            size = 116.dp,
                            preferHighRes = true,
                            fontSize = 42.sp
                        )
                    }
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

                // Saved Note Badge for this Caller
                if (!callInfo.activeNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Color(0xFF93C5FD), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = callInfo.activeNote!!,
                                fontSize = 12.sp,
                                color = Color(0xFFE0F2FE),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Multi-Call / Call Waiting Banner
                if (callInfo.hasSecondaryCall) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = callInfo.secondaryCallName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                val secStatusLabel = when (callInfo.secondaryCallStatus) {
                                    CallStatus.RINGING -> "Waiting call..."
                                    CallStatus.HOLDING -> "On Hold"
                                    else -> "Secondary call"
                                }
                                Text(
                                    text = secStatusLabel,
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    onClick = { CallManager.swapCalls() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0284C7)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = if (callInfo.secondaryCallStatus == CallStatus.RINGING) "Answer" else "Swap",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                if (callInfo.canMergeCalls) {
                                    Surface(
                                        onClick = { CallManager.mergeCalls() },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF16A34A)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CallMerge, contentDescription = "Merge", tint = Color.White, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Merge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentTheme == "CYBERPUNK_AMOLED") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.height(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(eq1, eq2, eq3, eq4, eq5).forEachIndexed { index, barHeight ->
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(barHeight.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (index % 2 == 0) Color(0xFF00F0FF) else Color(0xFFFF007F))
                                )
                            }
                        }
                    }
                }

                if (callInfo.isIpCall || callInfo.simSlot == 3) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val quality = callInfo.networkQuality
                    val dotColor = when (quality?.qualityLevel) {
                        3 -> Color(0xFF10B981) // Green
                        2 -> Color(0xFF34D399) // Light Green
                        1 -> Color(0xFFF59E0B) // Amber
                        0 -> Color(0xFFEF4444) // Red
                        else -> Color(0xFF10B981)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.22f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA78BFA).copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val qualityText = if (quality != null && callInfo.status == CallStatus.ACTIVE) {
                                val lossText = if (quality.packetLossPct >= 1.0f) " • Loss ${quality.packetLossPct.toInt()}%" else ""
                                "🌐 IP Call • ${quality.label} • ${quality.rttMs}ms$lossText"
                            } else {
                                "🌐 IP Call • HD Audio"
                            }
                            Text(
                                text = qualityText,
                                color = Color(0xFFE9D5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
                            CallStatus.RINGING -> if (callInfo.isIncoming) "Incoming Call..." else "Ringing..."
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

                    val netBadge by remember(
                        callInfo.callId,
                        callInfo.status,
                        callInfo.isIpCall,
                        callInfo.simSlot,
                        callInfo.subscriptionId,
                        callInfo.isWifiCalling
                    ) {
                        NetworkTypeHelper.observeCallNetworkLabel(
                            context = context,
                            isIpCall = callInfo.isIpCall,
                            simSlot = callInfo.simSlot,
                            subscriptionId = callInfo.subscriptionId,
                            isWifiCalling = callInfo.isWifiCalling
                        )
                    }.collectAsState(
                        initial = NetworkTypeHelper.getCallNetworkLabel(
                            context = context,
                            isIpCall = callInfo.isIpCall,
                            simSlot = callInfo.simSlot,
                            subscriptionId = callInfo.subscriptionId,
                            isWifiCalling = callInfo.isWifiCalling
                        )
                    )
                    if (netBadge.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = netBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // In-Call Controls or Incoming Call Controls
            if (callInfo.status == CallStatus.RINGING && callInfo.isIncoming) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Reject with Quick Reply Pill
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showQuickRejectSheet = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = buttonBgColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reject with Message",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Incoming Call: Answer & Reject Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // Reject (Red with animated pulse ripple)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((72 + wave2 * 26).dp)
                                    .clip(CircleShape)
                                    .border(2.dp, CallEndRed.copy(alpha = (1f - wave2) * 0.75f), CircleShape)
                            )
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
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Decline", color = Color.White, fontSize = 14.sp)
                    }

                    // Answer (Green with animated pulse ripple)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((72 + wave1 * 26).dp)
                                    .clip(CircleShape)
                                    .border(2.dp, CallIncomingGreen.copy(alpha = (1f - wave1) * 0.75f), CircleShape)
                            )
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
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Answer", color = Color.White, fontSize = 14.sp)
                    }
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
                            buttonBg = buttonBgColor,
                            buttonActiveBg = buttonActiveColor,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                CallManager.toggleMute()
                            }
                        )

                        InCallCircleButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = showDtmfKeypad,
                            buttonBg = buttonBgColor,
                            buttonActiveBg = buttonActiveColor,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showDtmfKeypad = !showDtmfKeypad
                            }
                        )

                        val audioIcon = if (callInfo.isBluetoothOn) Icons.Default.Bluetooth else Icons.Default.VolumeUp
                        val audioLabel = if (callInfo.isBluetoothOn) "Bluetooth" else "Speaker"
                        val audioActive = callInfo.isSpeakerOn || callInfo.isBluetoothOn

                        InCallCircleButton(
                            icon = audioIcon,
                            label = audioLabel,
                            isActive = audioActive,
                            buttonBg = buttonBgColor,
                            buttonActiveBg = buttonActiveColor,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                CallManager.toggleSpeaker(context)
                            }
                        )
                    }

                    // Row 2: Add Call, Hold / Swap, Record Call
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallCircleButton(
                            icon = Icons.Default.Add,
                            label = "Add Call",
                            isActive = false,
                            buttonBg = buttonBgColor,
                            buttonActiveBg = buttonActiveColor,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val dialIntent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                context.startActivity(dialIntent)
                            }
                        )

                        if (!callInfo.isIpCall) {
                            InCallCircleButton(
                                icon = if (callInfo.isHolding) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = if (callInfo.isHolding) "Resume" else "Hold",
                                isActive = callInfo.isHolding,
                                buttonBg = buttonBgColor,
                                buttonActiveBg = buttonActiveColor,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    CallManager.toggleHold()
                                }
                            )
                        }

                        InCallRecordButton(
                            isRecording = callInfo.isRecording,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                CallManager.toggleRecording()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // End Call Button (Large Red Circular with animated ripple & drop shadow)
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size((72 + wave1 * 22).dp)
                                .clip(CircleShape)
                                .border(1.5.dp, CallEndRed.copy(alpha = (1f - wave1) * 0.5f), CircleShape)
                        )
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

        // Quick Reject Reply (SMS / WhatsApp) Overlay
        if (showQuickRejectSheet) {
            QuickRejectReplyOverlay(
                number = callInfo.number,
                onSendSms = { msg ->
                    showQuickRejectSheet = false
                    CallManager.reject()
                    SmsHelper.openSmsWithBody(context, callInfo.number, msg)
                    onDismiss()
                },
                onSendWhatsApp = { msg ->
                    showQuickRejectSheet = false
                    CallManager.reject()
                    WhatsAppHelper.openWhatsAppChat(context, callInfo.number, msg)
                    onDismiss()
                },
                onClose = { showQuickRejectSheet = false }
            )
        }
    }
}

@Composable
private fun QuickRejectReplyOverlay(
    number: String,
    onSendSms: (String) -> Unit,
    onSendWhatsApp: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val templates = remember { AppSettingsPreferences(context).getQuickReplyTemplates() }
    var useWhatsApp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD0B0F19))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reject with Message",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { useWhatsApp = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!useWhatsApp) Color(0xFF3B82F6) else Color(0xFF334155)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Message, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SMS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Surface(
                            onClick = { useWhatsApp = true },
                            shape = RoundedCornerShape(8.dp),
                            color = if (useWhatsApp) Color(0xFF16A34A) else Color(0xFF334155)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "To: $number",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                templates.forEach { msg ->
                    Surface(
                        onClick = {
                            if (useWhatsApp) onSendWhatsApp(msg) else onSendSms(msg)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Cancel",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onClose() }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun InCallCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    buttonBg: Color = CallButtonTranslucent,
    buttonActiveBg: Color = CallButtonActive,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (isActive) buttonActiveBg else buttonBg)
                .border(
                    width = 1.dp,
                    color = if (isActive) Color.White.copy(alpha = 0.5f) else Color(0x33FFFFFF),
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
