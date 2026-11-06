package com.smartdialer.app.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.smartdialer.app.data.model.CallType
import com.smartdialer.app.data.repository.IpCallMode
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.data.repository.IpCallStatsRepository
import com.smartdialer.app.telecom.SipEngine
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.CallMissedRed
import com.smartdialer.app.ui.theme.CallOutgoingBlue
import com.smartdialer.app.ui.theme.IpCallPurple
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted
import java.util.Locale

private enum class IpSettingsSubView {
    DASHBOARD,
    SETTINGS,
    ADVANCED
}

@Composable
fun IpAccountSettingsScreen(
    ipPreferences: IpCallPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val liveStatus by SipEngine.registrationStatus.collectAsState()
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    var subView by remember { mutableStateOf(IpSettingsSubView.DASHBOARD) }
    var ipBalance by remember { mutableStateOf(ipPreferences.balance) }
    var showRechargeDialog by remember { mutableStateOf(false) }
    var showQuickCallDialog by remember { mutableStateOf(false) }
    var refreshStatsKey by remember { mutableStateOf(0) }

    val historyList = remember(refreshStatsKey) { IpCallStatsRepository.getCallHistory(context) }
    val contactStats = remember(refreshStatsKey) { IpCallStatsRepository.getTalkTimeByContact(context) }
    val totalTalkSeconds = remember(refreshStatsKey) { IpCallStatsRepository.getTotalTalkTimeSeconds(context) }

    // Main Credentials
    var username by remember { mutableStateOf(ipPreferences.username) }
    var password by remember { mutableStateOf(ipPreferences.password) }
    var domain by remember { mutableStateOf(ipPreferences.domain) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Advanced Credentials
    var outboundProxy by remember { mutableStateOf(ipPreferences.outboundProxy) }
    var displayName by remember { mutableStateOf(ipPreferences.displayName) }
    var authName by remember { mutableStateOf(ipPreferences.authName) }
    var voicemail by remember { mutableStateOf(ipPreferences.voicemail) }

    // Transport Protocol
    var transportProtocol by remember { mutableStateOf(ipPreferences.transportProtocol) }

    // TLS Certificate Management
    var verifyTls by remember { mutableStateOf(ipPreferences.verifyTls) }

    // STUN
    var enableStun by remember { mutableStateOf(ipPreferences.enableStun) }
    var stunServer by remember { mutableStateOf(ipPreferences.stunServer) }
    var stunPort by remember { mutableStateOf(ipPreferences.stunPort) }

    // Mobile Network & Data
    var useMobileData by remember { mutableStateOf(ipPreferences.useMobileData) }

    // Logging
    var enableLogging by remember { mutableStateOf(ipPreferences.enableLogging) }

    // Prefix & Mode
    var ipPrefix by remember { mutableStateOf(ipPreferences.ipPrefix) }
    var ipMode by remember { mutableStateOf(ipPreferences.ipMode) }

    fun saveAll() {
        ipPreferences.username = username
        ipPreferences.password = password
        ipPreferences.domain = domain
        ipPreferences.outboundProxy = outboundProxy
        ipPreferences.displayName = displayName
        ipPreferences.authName = authName
        ipPreferences.voicemail = voicemail
        ipPreferences.transportProtocol = transportProtocol
        ipPreferences.verifyTls = verifyTls
        ipPreferences.enableStun = enableStun
        ipPreferences.stunServer = stunServer
        ipPreferences.stunPort = stunPort
        ipPreferences.useMobileData = useMobileData
        ipPreferences.enableLogging = enableLogging
        ipPreferences.ipPrefix = ipPrefix
        ipPreferences.ipMode = ipMode
    }

    if (showRechargeDialog) {
        IpBalanceDialog(
            currentBalance = ipBalance,
            onSaveBalance = { newBal ->
                ipPreferences.balance = newBal
                ipBalance = newBal
                refreshStatsKey++
            },
            onDismiss = { showRechargeDialog = false }
        )
    }

    if (showQuickCallDialog) {
        QuickIpCallDialog(
            onCall = { number ->
                ipPreferences.makeIpCall(number)
                refreshStatsKey++
            },
            onDismiss = { showQuickCallDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            when (subView) {
                                IpSettingsSubView.ADVANCED -> subView = IpSettingsSubView.SETTINGS
                                IpSettingsSubView.SETTINGS -> subView = IpSettingsSubView.DASHBOARD
                                IpSettingsSubView.DASHBOARD -> onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDark
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (subView) {
                                IpSettingsSubView.DASHBOARD -> "IP Phone Dashboard"
                                IpSettingsSubView.SETTINGS -> "IP Account & SIP Settings"
                                IpSettingsSubView.ADVANCED -> "Advanced Settings"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = when (subView) {
                                IpSettingsSubView.DASHBOARD -> "Balance, Minutes & Call Analytics"
                                IpSettingsSubView.SETTINGS -> "Configure SIP Account & Gateway"
                                IpSettingsSubView.ADVANCED -> "VoIP, Transport & Network"
                            },
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    // Settings shortcut icon when on Dashboard
                    if (subView == IpSettingsSubView.DASHBOARD) {
                        IconButton(onClick = { subView = IpSettingsSubView.SETTINGS }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "SIP Settings",
                                tint = PurplePrimary
                            )
                        }
                    }

                    // Status Badge
                    val isConfigured = username.isNotBlank() || domain.isNotBlank()
                    val (badgeText, badgeColor) = when {
                        liveStatus.startsWith("Online") -> "Online / Registered" to CallIncomingGreen
                        liveStatus.startsWith("Connecting") -> "Connecting..." to Color(0xFFF59E0B)
                        liveStatus.startsWith("Auth Failed") -> "Auth Failed" to Color(0xFFEF4444)
                        isConfigured -> "Configured" to CallIncomingGreen
                        else -> "Offline" to TextMuted
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        containerColor = BgLight
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BgLight)
        ) {
            when (subView) {
                IpSettingsSubView.DASHBOARD -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Account & Balance Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(IpCallPurple, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (username.isNotBlank()) username else "No IP Account Set",
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextDark
                                            )
                                            Text(
                                                text = if (domain.isNotBlank()) domain else "SIP Domain not set",
                                                fontSize = 12.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    // Live status dot
                                    val (badgeText, badgeColor) = when {
                                        liveStatus.startsWith("Online") -> "Online" to CallIncomingGreen
                                        liveStatus.startsWith("Connecting") -> "Connecting" to Color(0xFFF59E0B)
                                        liveStatus.startsWith("Auth Failed") -> "Auth Failed" to Color(0xFFEF4444)
                                        username.isNotBlank() || domain.isNotBlank() -> "Configured" to CallIncomingGreen
                                        else -> "Offline" to TextMuted
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(badgeColor, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = badgeText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Spacer(modifier = Modifier.height(14.dp))

                                // Balance Display
                                Text(
                                    text = "IP Account Balance (বর্তমান ব্যালেন্স)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "৳ ${String.format(Locale.US, "%.2f", ipBalance)}",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = IpCallPurple
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showRechargeDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = PurplePrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Recharge", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                                        }

                                        Button(
                                            onClick = { showQuickCallDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = IpCallPurple),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("IP Call", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Summary Stats Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val totalMins = totalTalkSeconds / 60
                            val totalSecs = totalTalkSeconds % 60
                            val timeStr = if (totalMins > 0) "${totalMins}m ${totalSecs}s" else "${totalSecs}s"

                            DashboardStatBox(
                                title = "Total Minutes",
                                value = timeStr,
                                subtitle = "মোট কথা বলা",
                                icon = Icons.Default.Timer,
                                iconTint = PurplePrimary,
                                modifier = Modifier.weight(1f)
                            )

                            DashboardStatBox(
                                title = "Total Calls",
                                value = "${historyList.size}",
                                subtitle = "মোট আইপি কল",
                                icon = Icons.Default.Call,
                                iconTint = CallOutgoingBlue,
                                modifier = Modifier.weight(1f)
                            )

                            val outCalls = historyList.count { it.callType == CallType.OUTGOING }
                            val inCalls = historyList.count { it.callType == CallType.INCOMING }
                            DashboardStatBox(
                                title = "Out / In",
                                value = "$outCalls / $inCalls",
                                subtitle = "আউট / ইন কল",
                                icon = Icons.Default.CallMade,
                                iconTint = CallIncomingGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 3. Settings Option Row Card (User requested: "sob kichu oitar moddhe settings name ekta option kore tar moddhe die dreo")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { subView = IpSettingsSubView.SETTINGS },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PurpleUltraLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = PurplePrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "SIP & Account Settings (সেটিংস)",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Username, Password, Domain, Mode, Proxy & STUN",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TextLightGray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 4. Minutes Spoken by Contact Card ("kar sathe koto minutes bolchi")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Talk Time by Contact",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = "কার সাথে কত মিনিট কথা বলেছেন",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Spacer(modifier = Modifier.height(6.dp))

                                if (contactStats.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No contact talk time yet.\nআইপি কল সম্পন্ন হলে কার সাথে কত মিনিট কথা বলেছেন এখানে দেখাবে।",
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                } else {
                                    contactStats.take(10).forEach { stat ->
                                        ContactTalkRow(
                                            stat = stat,
                                            onCallClick = { number ->
                                                ipPreferences.makeIpCall(number)
                                            }
                                        )
                                        HorizontalDivider(color = Color(0xFFF8FAFC))
                                    }
                                }
                            }
                        }

                        // 5. Recent IP Call History Card ("histry")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Recent IP Call History",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = "আইপি কল হিস্ট্রি",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }

                                    if (historyList.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                IpCallStatsRepository.clearHistory(context)
                                                refreshStatsKey++
                                            }
                                        ) {
                                            Text("Clear", fontSize = 12.sp, color = CallMissedRed)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Spacer(modifier = Modifier.height(6.dp))

                                if (historyList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No IP calls recorded yet.\nআইপি নম্বর দিয়ে কল করার পর কল হিস্ট্রি এখানে দেখা যাবে।",
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                } else {
                                    historyList.take(20).forEach { record ->
                                        IpCallHistoryRow(
                                            record = record,
                                            onCallClick = { number ->
                                                ipPreferences.makeIpCall(number)
                                            }
                                        )
                                        HorizontalDivider(color = Color(0xFFF8FAFC))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                IpSettingsSubView.SETTINGS -> {
                    // ============================================
                    // SIP ACCOUNT SETUP (Settings SubView)
                    // ============================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Account Credentials Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(IpCallPurple),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SIP Account",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = "Enter your IP Phone / VoIP credentials",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            // Username
                            Column {
                                Text(
                                    text = "Username",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    placeholder = { Text("e.g. 09649732389", color = TextLightGray, fontSize = 14.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedIndicatorColor = PurplePrimary,
                                        unfocusedIndicatorColor = Color(0xFFE2E8F0)
                                    )
                                )
                            }

                            // Password
                            Column {
                                Text(
                                    text = "Password",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = { Text("Password", color = TextLightGray, fontSize = 14.sp) },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                                tint = TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedIndicatorColor = PurplePrimary,
                                        unfocusedIndicatorColor = Color(0xFFE2E8F0)
                                    )
                                )
                            }

                            // SIP Domain
                            Column {
                                Text(
                                    text = "SIP Domain",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = domain,
                                    onValueChange = { domain = it },
                                    placeholder = { Text("e.g. sip.domain.com or IP address", color = TextLightGray, fontSize = 14.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedIndicatorColor = PurplePrimary,
                                        unfocusedIndicatorColor = Color(0xFFE2E8F0)
                                    )
                                )
                            }
                        }
                    }

                    // Dialing Mode Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "IP Dialing Mode",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ModeSelectRow(
                                title = "SIP Protocol (Direct sip: URI)",
                                isSelected = ipMode == IpCallMode.SIP,
                                onClick = { ipMode = IpCallMode.SIP }
                            )

                            ModeSelectRow(
                                title = "Prefix Routing (e.g. 096 + Number)",
                                isSelected = ipMode == IpCallMode.PREFIX,
                                onClick = { ipMode = IpCallMode.PREFIX }
                            )

                            ModeSelectRow(
                                title = "VoIP App Chooser (PortSIP, Alaap, etc.)",
                                isSelected = ipMode == IpCallMode.APP_CHOOSER,
                                onClick = { ipMode = IpCallMode.APP_CHOOSER }
                            )
                        }
                    }

                    // "Advanced" Navigation Card (As in PortSIP video)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { subView = IpSettingsSubView.ADVANCED },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PurpleUltraLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Advanced",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = "Proxy, Transport, STUN, Mobile Network",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextLightGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                saveAll()
                                SipEngine.startListening(context)
                                subView = IpSettingsSubView.DASHBOARD
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IpCallPurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Save & Connect Account",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Test Connection Button
                        OutlinedButton(
                            onClick = {
                                saveAll()
                                isTestingConnection = true
                                testResultText = "Testing SIP connection to server..."
                                SipEngine.registerAccount(context) { success, msg ->
                                    isTestingConnection = false
                                    testResultText = msg
                                    if (success) {
                                        SipEngine.startListening(context)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isTestingConnection) "Testing Connection..." else "Test SIP Connection",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IpCallPurple
                            )
                        }

                        if (!testResultText.isNullOrBlank()) {
                            Text(
                                text = testResultText!!,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (liveStatus.startsWith("Online")) CallIncomingGreen else Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        if (ipPreferences.isConfigured) {
                            OutlinedButton(
                                onClick = {
                                    SipEngine.stopListening()
                                    ipPreferences.clearAccount()
                                    username = ""
                                    password = ""
                                    domain = ""
                                    outboundProxy = ""
                                    displayName = ""
                                    authName = ""
                                    voicemail = ""
                                    stunServer = ""
                                    ipPrefix = ""
                                    testResultText = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Clear / Disconnect Account",
                                    fontSize = 14.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                IpSettingsSubView.ADVANCED -> {
                    // ============================================
                    // ADVANCED SETTINGS (Exact System From Video)
                    // ============================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ACCOUNT SETTINGS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ACCOUNT SETTINGS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            // Outbound Proxy
                            AdvancedInputField(
                                label = "Outbound Proxy",
                                value = outboundProxy,
                                onValueChange = { outboundProxy = it },
                                placeholder = "[Domain is set as default]"
                            )

                            // Display as
                            AdvancedInputField(
                                label = "Display as",
                                value = displayName,
                                onValueChange = { displayName = it },
                                placeholder = "[Display name is set as default]"
                            )

                            // Auth Name
                            AdvancedInputField(
                                label = "Auth Name",
                                value = authName,
                                onValueChange = { authName = it },
                                placeholder = "[Username is set as default]"
                            )

                            // Voicemail
                            AdvancedInputField(
                                label = "Voicemail",
                                value = voicemail,
                                onValueChange = { voicemail = it },
                                placeholder = "[Voicemail number]"
                            )
                        }
                    }

                    // TRANSPORT PROTOCOL
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TRANSPORT PROTOCOL",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            ProtocolOptionRow(
                                title = "UDP",
                                isSelected = transportProtocol.equals("UDP", ignoreCase = true),
                                onClick = { transportProtocol = "UDP" }
                            )

                            ProtocolOptionRow(
                                title = "TLS",
                                isSelected = transportProtocol.equals("TLS", ignoreCase = true),
                                onClick = { transportProtocol = "TLS" }
                            )

                            ProtocolOptionRow(
                                title = "TCP",
                                isSelected = transportProtocol.equals("TCP", ignoreCase = true),
                                onClick = { transportProtocol = "TCP" }
                            )
                        }
                    }

                    // TLS CERTIFICATES MANAGEMENT
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TLS CERTIFICATES MANAGEMENT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Verify TLS Certificates",
                                    fontSize = 14.sp,
                                    color = TextDark,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = verifyTls,
                                    onCheckedChange = { verifyTls = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                                )
                            }
                        }
                    }

                    // STUN
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "STUN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enable STUN",
                                    fontSize = 14.sp,
                                    color = TextDark,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = enableStun,
                                    onCheckedChange = { enableStun = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                                )
                            }

                            if (enableStun) {
                                AdvancedInputField(
                                    label = "Server",
                                    value = stunServer,
                                    onValueChange = { stunServer = it },
                                    placeholder = "e.g. stun.l.google.com"
                                )

                                AdvancedInputField(
                                    label = "Port",
                                    value = stunPort,
                                    onValueChange = { stunPort = it },
                                    placeholder = "3478"
                                )
                            }
                        }
                    }

                    // MOBILE NETWORK
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "MOBILE NETWORK",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Mobile Network",
                                        fontSize = 14.sp,
                                        color = TextDark,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Enable this option to allow the app to use your mobile data plan when Wi-Fi is not available.",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                Switch(
                                    checked = useMobileData,
                                    onCheckedChange = { useMobileData = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                                )
                            }
                        }
                    }

                    // LOGGING & PREFIX
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ADDITIONAL PREFERENCES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enable Logging",
                                    fontSize = 14.sp,
                                    color = TextDark,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = enableLogging,
                                    onCheckedChange = { enableLogging = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary)
                                )
                            }

                            AdvancedInputField(
                                label = "Dialing Prefix (Optional)",
                                value = ipPrefix,
                                onValueChange = { ipPrefix = it },
                                placeholder = "e.g. 096"
                            )
                        }
                    }

                    // Save Advanced Button
                    Button(
                        onClick = {
                            saveAll()
                            subView = IpSettingsSubView.SETTINGS
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IpCallPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Save Advanced Settings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
}

@Composable
private fun AdvancedInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextLightGray, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC),
                focusedIndicatorColor = PurplePrimary,
                unfocusedIndicatorColor = Color(0xFFE2E8F0)
            )
        )
    }
}

@Composable
private fun ProtocolOptionRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = if (isSelected) TextDark else TextMuted,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ModeSelectRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = PurplePrimary)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            color = if (isSelected) TextDark else TextMuted,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
