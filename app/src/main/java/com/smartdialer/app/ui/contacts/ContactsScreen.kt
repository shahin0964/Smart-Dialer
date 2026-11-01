package com.smartdialer.app.ui.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.ContactCounts
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.data.model.ContactSaveDestinationType
import com.smartdialer.app.data.model.ContactSourceFilter
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.ui.components.ContactAvatar
import com.smartdialer.app.ui.components.PermissionCard
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.DividerColor
import com.smartdialer.app.ui.theme.PurpleBorder
import com.smartdialer.app.ui.theme.PurpleLight
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.READ_CONTACTS] == true
        if (hasPermission) {
            viewModel.loadContacts()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadContacts()
        }
    }

    val contacts by viewModel.contacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val googleAccounts by viewModel.googleAccounts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedContact by viewModel.selectedContact.collectAsState()
    val showDeletedDialog by viewModel.showDeletedDialog.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val showAddContactDialog by viewModel.showAddContactDialog.collectAsState()
    val availableDestinations by viewModel.availableDestinations.collectAsState()
    val isSavingContact by viewModel.isSavingContact.collectAsState()

    // Group contacts alphabetically
    val grouped = remember(contacts) {
        contacts.groupBy { it.initial }.toSortedMap()
    }

    val alphabet = remember { ('A'..'Z').map { it.toString() } + "#" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
    ) {
        // Search Bar + Add Contact Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search contacts", color = TextLightGray, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextLightGray,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextLightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = PurplePrimary,
                    unfocusedIndicatorColor = Color(0xFFE2E8F0)
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = { viewModel.createNewContact(context) },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Contact",
                    tint = PurplePrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Account Filter Chips Row (All, Google/Gmail, Device, SIM, Sync)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccountFilterChip(
                label = "All",
                count = counts.total,
                icon = Icons.Default.AccountCircle,
                isSelected = activeFilter == ContactSourceFilter.ALL,
                onClick = { viewModel.setFilter(ContactSourceFilter.ALL) }
            )

            AccountFilterChip(
                label = "Google",
                count = counts.google,
                icon = Icons.Default.Cloud,
                iconColor = Color(0xFFEA4335), // Google Red
                isSelected = activeFilter == ContactSourceFilter.GOOGLE,
                onClick = { viewModel.setFilter(ContactSourceFilter.GOOGLE) }
            )

            AccountFilterChip(
                label = "Phone",
                count = counts.device,
                icon = Icons.Default.PhoneAndroid,
                isSelected = activeFilter == ContactSourceFilter.DEVICE,
                onClick = { viewModel.setFilter(ContactSourceFilter.DEVICE) }
            )

            AccountFilterChip(
                label = "SIM",
                count = counts.sim,
                icon = Icons.Default.SimCard,
                isSelected = activeFilter == ContactSourceFilter.SIM,
                onClick = { viewModel.setFilter(ContactSourceFilter.SIM) }
            )

            // Direct Sync Google Contacts Button Chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, PurpleBorder),
                modifier = Modifier.clickable { viewModel.syncGoogleContacts() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = PurplePrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Google",
                            tint = PurplePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sync Google",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PurplePrimary
                    )
                }
            }
        }

        // Sync Status Message Banner
        AnimatedVisibility(
            visible = !syncMessage.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            syncMessage?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFECFDF5),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = Color(0xFF065F46),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (!hasPermission) {
            PermissionCard(
                title = "Contacts Permission Required",
                description = "To view, search, and call your Google and phone contacts, Smart Dialer requires permission to access your contacts.",
                buttonText = "Grant Contacts Permission",
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS,
                            Manifest.permission.CALL_PHONE
                        )
                    )
                }
            )
        } else if (contacts.isEmpty() && !isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (activeFilter == ContactSourceFilter.GOOGLE) Icons.Default.Cloud else Icons.Default.Person,
                        contentDescription = null,
                        tint = TextLightGray,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = when {
                            searchQuery.isNotEmpty() -> "No contacts matching \"$searchQuery\""
                            activeFilter == ContactSourceFilter.GOOGLE -> "No Google contacts found on this device"
                            activeFilter == ContactSourceFilter.DEVICE -> "No device storage contacts found"
                            activeFilter == ContactSourceFilter.SIM -> "No SIM card contacts found"
                            else -> "No contacts found"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )

                    if (activeFilter == ContactSourceFilter.GOOGLE) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (googleAccounts.isNotEmpty()) {
                                "Connected: ${googleAccounts.joinToString(", ")}"
                            } else {
                                "Ensure Google Contacts sync is enabled in phone settings"
                            },
                            fontSize = 12.sp,
                            color = TextLightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.syncGoogleContacts() },
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.openGoogleSyncSettings() },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Sync Settings", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Header: Account / Filter Title + Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = when (activeFilter) {
                            ContactSourceFilter.ALL -> "All Contacts (${contacts.size})"
                            ContactSourceFilter.GOOGLE -> "Google Contacts (${contacts.size})"
                            ContactSourceFilter.DEVICE -> "Device Contacts (${contacts.size})"
                            ContactSourceFilter.SIM -> "SIM Contacts (${contacts.size})"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    if (activeFilter == ContactSourceFilter.GOOGLE && googleAccounts.isNotEmpty()) {
                        Text(
                            text = googleAccounts.firstOrNull() ?: "",
                            fontSize = 11.sp,
                            color = PurplePrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PurplePrimary
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 28.dp)
                ) {
                    for ((letter, contactList) in grouped) {
                        item(key = "header_$letter") {
                            Text(
                                text = letter,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        items(contactList, key = { it.id }) { contact ->
                            ContactRow(
                                contact = contact,
                                onCallClick = { viewModel.callContact(context, contact.phoneNumber) },
                                onRowClick = { viewModel.selectContact(contact) }
                            )
                        }
                    }

                    // Deleted Contacts Card at bottom
                    item {
                        DeletedContactsCard(
                            onClick = { viewModel.setShowDeletedDialog(true) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Alphabet Fast-Scroller on Right Edge
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (char in alphabet) {
                        Text(
                            text = char,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grouped.containsKey(char)) PurplePrimary else TextLightGray.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable {
                                    if (grouped.containsKey(char)) {
                                        var targetIndex = 0
                                        for ((key, list) in grouped) {
                                            if (key == char) break
                                            targetIndex += 1 + list.size
                                        }
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                }
                                .padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }

    // Contact Details Modal Dialog
    selectedContact?.let { contact ->
        ContactDetailsDialog(
            contact = contact,
            onDismiss = { viewModel.selectContact(null) },
            onCall = { number -> viewModel.callContact(context, number) },
            onSms = { number -> viewModel.sendSms(context, number) },
            onEmail = { email -> viewModel.sendEmail(context, email) },
            onEdit = { viewModel.editContact(context, contact) }
        )
    }

    // Deleted Contacts Dialog
    if (showDeletedDialog) {
        DeletedContactsDialog(
            onDismiss = { viewModel.setShowDeletedDialog(false) },
            onOpenTrash = {
                viewModel.openGoogleContactsTrash()
                viewModel.setShowDeletedDialog(false)
            },
            onOpenSettings = {
                viewModel.openGoogleSyncSettings()
                viewModel.setShowDeletedDialog(false)
            }
        )
    }

    // Add New Contact Modal Dialog
    if (showAddContactDialog) {
        AddNewContactDialog(
            destinations = availableDestinations,
            initialDestinationType = when (activeFilter) {
                ContactSourceFilter.GOOGLE -> ContactSaveDestinationType.GOOGLE
                ContactSourceFilter.DEVICE -> ContactSaveDestinationType.PHONE
                ContactSourceFilter.SIM -> ContactSaveDestinationType.SIM
                else -> ContactSaveDestinationType.GOOGLE
            },
            isSaving = isSavingContact,
            onDismiss = { viewModel.closeAddContactDialog() },
            onSave = { name, phone, secPhone, email, company, dest ->
                viewModel.saveContact(
                    name = name,
                    phoneNumber = phone,
                    secondaryNumber = secPhone,
                    email = email,
                    company = company,
                    destination = dest,
                    onComplete = { _, _ -> }
                )
            }
        )
    }
}

@Composable
fun AccountFilterChip(
    label: String,
    count: Int,
    icon: ImageVector,
    iconColor: Color? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PurplePrimary else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) PurplePrimary else Color(0xFFE2E8F0)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else (iconColor ?: TextMuted),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label ($count)",
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextDark
            )
        }
    }
}

@Composable
fun ContactRow(
    contact: ContactItem,
    onCallClick: () -> Unit,
    onRowClick: () -> Unit
) {
    val avatarColors = listOf(
        Color(0xFFDBEAFE) to Color(0xFF1D4ED8), // Blue
        Color(0xFFFEF3C7) to Color(0xFFB45309), // Amber
        Color(0xFFDCFCE7) to Color(0xFF15803D), // Green
        Color(0xFFEDE9FE) to Color(0xFF6D28D9), // Purple
        Color(0xFFFFE4E6) to Color(0xFFBE123C)  // Pink
    )
    val colorPair = avatarColors[Math.abs(contact.name.hashCode()) % avatarColors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            ContactAvatar(
                name = contact.name,
                photoUri = contact.photoUri,
                contactId = contact.id,
                size = 44.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (contact.isGoogleContact) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEE2E2), // Light red/coral for Google
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFCA5A5))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "G",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFDC2626)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Google",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = contact.displaySubtitle,
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (contact.phoneNumber.isNotBlank()) {
            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ContactDetailsDialog(
    contact: ContactItem,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onEmail: (String) -> Unit,
    onEdit: () -> Unit
) {
    val avatarColors = listOf(
        Color(0xFFDBEAFE) to Color(0xFF1D4ED8),
        Color(0xFFFEF3C7) to Color(0xFFB45309),
        Color(0xFFDCFCE7) to Color(0xFF15803D),
        Color(0xFFEDE9FE) to Color(0xFF6D28D9),
        Color(0xFFFFE4E6) to Color(0xFFBE123C)
    )
    val colorPair = avatarColors[Math.abs(contact.name.hashCode()) % avatarColors.size]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ContactAvatar(
                    name = contact.name,
                    photoUri = contact.photoUri,
                    contactId = contact.id,
                    size = 72.dp,
                    preferHighRes = true,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = contact.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Account Source Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (contact.isGoogleContact) Color(0xFFFEE2E2) else PurpleUltraLight,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (contact.isGoogleContact) Color(0xFFFCA5A5) else PurpleBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (contact.isGoogleContact) Icons.Default.Cloud else Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (contact.isGoogleContact) Color(0xFFDC2626) else PurplePrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (contact.isGoogleContact) {
                                "Google: ${contact.accountName ?: "Synced Account"}"
                            } else {
                                contact.accountName ?: "Phone Storage"
                            },
                            fontSize = 11.sp,
                            color = if (contact.isGoogleContact) Color(0xFF991B1B) else PurplePrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Phone Numbers
                if (contact.phoneNumbers.isNotEmpty()) {
                    Text(
                        text = "Phone Numbers",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    for (phone in contact.phoneNumbers) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = phone,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        onCall(phone)
                                        onDismiss()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call",
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onSms(phone)
                                        onDismiss()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Message,
                                        contentDescription = "SMS",
                                        tint = Color(0xFF0284C7),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Emails
                if (contact.emails.isNotEmpty()) {
                    Text(
                        text = "Email Addresses",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    for (email in contact.emails) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = email,
                                fontSize = 13.sp,
                                color = TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    onEmail(email)
                                    onDismiss()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Send Email",
                                    tint = Color(0xFFEA4335),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Block & Report Spam Action
                if (contact.phoneNumber.isNotBlank()) {
                    val appSettings = remember { AppSettingsPreferences(context) }
                    var isBlocked by remember { mutableStateOf(appSettings.isBlocked(contact.phoneNumber)) }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            if (isBlocked) {
                                appSettings.removeBlockedNumber(contact.phoneNumber)
                                appSettings.unreportSpam(contact.phoneNumber)
                                isBlocked = false
                            } else {
                                appSettings.reportSpam(contact.phoneNumber)
                                isBlocked = true
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isBlocked) Color(0xFF10B981) else Color(0xFFEF4444)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isBlocked) Color(0xFF10B981) else Color(0xFFEF4444).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Default.Check else Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBlocked) "Unblock Number" else "Report as Spam & Block",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEdit()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Contact", fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun DeletedContactsDialog(
    onDismiss: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PurpleUltraLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Deleted Contacts", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Contacts deleted from your Google / Gmail account are safely stored in Google Contacts Trash for 30 days before permanent deletion.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
                Text(
                    text = "You can view, restore, or permanently remove deleted contacts directly through Google Contacts.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenTrash,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Google Trash", fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Sync Settings", fontSize = 13.sp, color = TextDark)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun DeletedContactsCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PurpleUltraLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(PurplePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deleted Contacts",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Deleted Contacts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "View, recover, or permanently delete removed contacts",
                        fontSize = 12.sp,
                        color = TextMuted,
                        maxLines = 2,
                        lineHeight = 16.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
