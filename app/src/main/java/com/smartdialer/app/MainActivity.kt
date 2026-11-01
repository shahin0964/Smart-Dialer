package com.smartdialer.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.viewmodel.compose.viewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactsRepository = ContactsRepository(applicationContext)
        callLogRepository = CallLogRepository(applicationContext)
        simRepository = SimRepository(applicationContext)
        ipPreferences = IpCallPreferences(applicationContext)

        val initialNumber = extractDialNumber(intent)

        setContent {
            SmartDialerTheme(darkTheme = false) {
                MainContent(
                    initialNumber = initialNumber,
                    onRequestDefaultDialer = { requestDefaultDialerRole() }
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

    fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    startActivity(intent)
                }
            }
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager != null && telecomManager.defaultDialerPackage != packageName) {
                @Suppress("DEPRECATION")
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                startActivity(intent)
            }
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
                        onRequestDefaultDialer = onRequestDefaultDialer
                    )
                }
            }
        }
    }
}
