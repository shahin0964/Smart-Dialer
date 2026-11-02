package com.smartdialer.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.data.repository.CallLogRepository
import com.smartdialer.app.data.repository.ContactsRepository
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.data.repository.SimRepository
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.calls.CallsScreen
import com.smartdialer.app.ui.calls.CallsViewModel
import com.smartdialer.app.ui.components.MainTab
import com.smartdialer.app.ui.components.TopHeader
import com.smartdialer.app.ui.components.TopTabBar
import com.smartdialer.app.ui.contacts.ContactsScreen
import com.smartdialer.app.ui.contacts.ContactsViewModel
import com.smartdialer.app.ui.dialer.DialerScreen
import com.smartdialer.app.ui.dialer.DialerViewModel
import com.smartdialer.app.ui.more.MoreScreen
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.SmartDialerTheme

class MainActivity : ComponentActivity() {

    private lateinit var contactsRepository: ContactsRepository
    private lateinit var callLogRepository: CallLogRepository
    private lateinit var simRepository: SimRepository
    private lateinit var ipPreferences: IpCallPreferences
    private lateinit var appSettings: AppSettingsPreferences

    // Native ActivityResultLauncher for RoleManager & Default Dialer request
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Default dialer request completed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactsRepository = ContactsRepository(applicationContext)
        callLogRepository = CallLogRepository(applicationContext)
        simRepository = SimRepository(applicationContext)
        ipPreferences = IpCallPreferences(applicationContext)
        appSettings = AppSettingsPreferences(applicationContext)

        if (ipPreferences.isConfigured) {
            com.smartdialer.app.telecom.SipEngine.startListening(this)
        }

        val initialNumber = extractDialNumber(intent)
        val initialTab = extractInitialTab(intent)

        // If launched with ACTION_CALL (from system dialer chooser or third-party app)
        if (intent.action == Intent.ACTION_CALL && !initialNumber.isNullOrBlank()) {
            if (intent.data?.scheme == "sip") {
                CallManager.initiateIpCall(this, initialNumber)
            } else {
                CallManager.initiateCall(this, initialNumber)
            }
        }

        setContent {
            SmartDialerTheme(darkTheme = false) {
                MainContent(
                    initialNumber = initialNumber,
                    initialTab = initialTab,
                    onRequestDefaultDialer = { triggerDefaultDialerRequest() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val number = extractDialNumber(intent)
        if (intent.action == Intent.ACTION_CALL && !number.isNullOrBlank()) {
            if (intent.data?.scheme == "sip") {
                CallManager.initiateIpCall(this, number)
            } else {
                CallManager.initiateCall(this, number)
            }
        }
    }

    private fun extractDialNumber(intent: Intent?): String? {
        val uri: Uri? = intent?.data
        if (uri == null) return null
        return when (uri.scheme) {
            "tel" -> uri.schemeSpecificPart
            "sip" -> {
                val part = uri.schemeSpecificPart
                if (part.contains("@")) part.substringBefore("@") else part
            }
            else -> null
        }
    }

    private fun extractInitialTab(intent: Intent?): MainTab {
        if (intent == null) return MainTab.DIALER
        return when {
            intent.type == "vnd.android.cursor.dir/calls" -> MainTab.CALLS
            intent.type == "vnd.android.cursor.dir/contact" || intent.type == "vnd.android.cursor.dir/person" -> MainTab.CONTACTS
            else -> MainTab.DIALER
        }
    }

    fun triggerDefaultDialerRequest() {
        // Attempt 1: RoleManager for Android 10+ (Q / 14 / 15 / 16 / 17)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    defaultDialerLauncher.launch(intent)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Attempt 2: TelecomManager ACTION_CHANGE_DEFAULT_DIALER
        try {
            @Suppress("DEPRECATION")
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            defaultDialerLauncher.launch(intent)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Attempt 3: Direct to Android Default Apps Settings (MIUI, HyperOS, Samsung One UI, Oppo, Vivo)
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Attempt 4: App Details Settings
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Composable
    private fun MainContent(
        initialNumber: String?,
        initialTab: MainTab,
        onRequestDefaultDialer: () -> Unit
    ) {
        var selectedTab by remember { mutableStateOf(initialTab) }

        val dialerViewModel = remember {
            DialerViewModel(contactsRepository, simRepository, ipPreferences)
        }
        val callsViewModel = remember {
            CallsViewModel(callLogRepository)
        }
        val contactsViewModel = remember {
            ContactsViewModel(contactsRepository)
        }

        LaunchedEffect(initialNumber) {
            if (!initialNumber.isNullOrBlank()) {
                dialerViewModel.setNumber(initialNumber)
                selectedTab = MainTab.DIALER
            }
        }

        // Permissions Launcher on startup
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.READ_CONTACTS] == true) {
                contactsViewModel.loadContacts()
            }
            if (permissions[Manifest.permission.READ_CALL_LOG] == true) {
                callsViewModel.loadCallLogs()
            }
            dialerViewModel.loadSims()
        }

        LaunchedEffect(Unit) {
            val permissionsList = mutableListOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missing = permissionsList.filter {
                ContextCompat.checkSelfPermission(applicationContext, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }

        Scaffold(
            topBar = {
                Column {
                    TopHeader(
                        onCrownClick = { /* VIP / Crown info */ },
                        onSettingsClick = { selectedTab = MainTab.MORE },
                        onDefaultDialerClick = onRequestDefaultDialer
                    )
                    TopTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
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
                when (selectedTab) {
                    MainTab.DIALER -> DialerScreen(viewModel = dialerViewModel)
                    MainTab.CALLS -> CallsScreen(viewModel = callsViewModel)
                    MainTab.CONTACTS -> ContactsScreen(viewModel = contactsViewModel)
                    MainTab.MORE -> MoreScreen(
                        ipPreferences = ipPreferences,
                        simRepository = simRepository,
                        appSettings = appSettings,
                        onRequestDefaultDialer = onRequestDefaultDialer
                    )
                }
            }
        }
    }
}
