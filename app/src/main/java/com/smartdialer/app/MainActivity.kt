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
import androidx.activity.result.ActivityResultLauncher
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

    private var defaultDialerLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactsRepository = ContactsRepository(applicationContext)
        callLogRepository = CallLogRepository(applicationContext)
        simRepository = SimRepository(applicationContext)
        ipPreferences = IpCallPreferences(applicationContext)
        appSettings = AppSettingsPreferences(applicationContext)

        val initialNumber = extractDialNumber(intent)

        setContent {
            SmartDialerTheme(darkTheme = false) {
                MainContent(
                    initialNumber = initialNumber,
                    onRequestDefaultDialer = { triggerDefaultDialerRequest() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractDialNumber(intent: Intent?): String? {
        val uri: Uri? = intent?.data
        return if (uri != null && uri.scheme == "tel") {
            uri.schemeSpecificPart
        } else {
            null
        }
    }

    fun triggerDefaultDialerRequest() {
        // Attempt 1: RoleManager for Android 10+ (Q)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    if (defaultDialerLauncher != null) {
                        defaultDialerLauncher?.launch(intent)
                    } else {
                        startActivity(intent)
                    }
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
            if (defaultDialerLauncher != null) {
                defaultDialerLauncher?.launch(intent)
            } else {
                startActivity(intent)
            }
            return
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Attempt 3: Direct to Android Default Apps Settings (MIUI, HyperOS, Samsung, Oppo, Vivo)
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
        onRequestDefaultDialer: () -> Unit
    ) {
        var selectedTab by remember { mutableStateOf(MainTab.DIALER) }

        val dialerViewModel = remember {
            DialerViewModel(contactsRepository, simRepository, ipPreferences)
        }
        val callsViewModel = remember {
            CallsViewModel(callLogRepository)
        }
        val contactsViewModel = remember {
            ContactsViewModel(contactsRepository)
        }

        val roleLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            // Role dialog finished
        }
        LaunchedEffect(roleLauncher) {
            defaultDialerLauncher = roleLauncher
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
            val required = arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG
            )
            val missing = required.filter {
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
