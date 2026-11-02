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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.smartdialer.app.data.repository.IpCallMode
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.telecom.SipEngine
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.IpCallPurple
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted

private enum class IpSettingsSubView {
    MAIN,
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

    var subView by remember { mutableStateOf(IpSettingsSubView.MAIN) }

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
                            if (subView == IpSettingsSubView.ADVANCED) {
                                subView = IpSettingsSubView.MAIN
                            } else {
                                onBack()
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
                            text = if (subView == IpSettingsSubView.ADVANCED) "Advanced Settings" else "IP Calling Account (SIP)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = if (subView == IpSettingsSubView.ADVANCED) "VoIP, Transport & Network" else "Configure SIP Account & Gateway",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
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
            if (subView == IpSettingsSubView.MAIN) {
                // ============================================
                // MAIN SIP ACCOUNT SETUP (Like PortSIP Main)
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
                                SipEngine.registerAccount(context) { _, _ -> }
                                onBack()
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
            } else {
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
                            subView = IpSettingsSubView.MAIN
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
