package com.smartdialer.app.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.ui.components.PermissionCard
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.PurpleBorder
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
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

        if (!hasPermission) {
            PermissionCard(
                title = "Contacts Permission Required",
                description = "To view, search, and call your real device contacts, Smart Dialer requires permission to access your contacts.",
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
        } else if (contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextLightGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No contacts matching \"$searchQuery\"" else "No contacts found on device",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }
            }
        } else {
            // Header: All Contacts (Count)
            Text(
                text = "All Contacts (${contacts.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 28.dp)
                ) {
                    grouped.forEach { (letter, contactList) ->
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
                                onCallClick = { viewModel.callContact(context, contact.phoneNumber) }
                            )
                        }
                    }

                    // Deleted Contacts Card at bottom
                    item {
                        DeletedContactsCard(
                            onClick = { /* Navigate or open deleted contacts */ }
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
                    alphabet.forEach { char ->
                        Text(
                            text = char,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grouped.containsKey(char)) PurplePrimary else TextLightGray.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable {
                                    if (grouped.containsKey(char)) {
                                        // Calculate item index
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
}

@Composable
fun ContactRow(
    contact: ContactItem,
    onCallClick: () -> Unit
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
            .clickable { onCallClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorPair.first),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.initial,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorPair.second
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = contact.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

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
