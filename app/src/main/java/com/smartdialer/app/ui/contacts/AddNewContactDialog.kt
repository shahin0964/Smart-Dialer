package com.smartdialer.app.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartdialer.app.data.model.ContactSaveDestination
import com.smartdialer.app.data.model.ContactSaveDestinationType
import com.smartdialer.app.ui.theme.DividerColor
import com.smartdialer.app.ui.theme.PurpleBorder
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted

@Composable
fun AddNewContactDialog(
    destinations: List<ContactSaveDestination>,
    initialDestinationType: ContactSaveDestinationType = ContactSaveDestinationType.GOOGLE,
    initialName: String = "",
    initialPhone: String = "",
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        phoneNumber: String,
        secondaryNumber: String?,
        email: String?,
        company: String?,
        destination: ContactSaveDestination
    ) -> Unit
) {
    // Default destination
    val fallbackDestination = remember(destinations, initialDestinationType) {
        destinations.firstOrNull { it.type == initialDestinationType }
            ?: destinations.firstOrNull()
            ?: ContactSaveDestination(
                type = ContactSaveDestinationType.GOOGLE,
                title = "Google Account",
                subtitle = "Google Cloud Sync"
            )
    }

    var selectedDestination by remember(destinations, initialDestinationType) {
        mutableStateOf(fallbackDestination)
    }

    var name by remember(initialName) { mutableStateOf(initialName) }
    var phoneNumber by remember(initialPhone) { mutableStateOf(initialPhone) }
    var secondaryNumber by remember { mutableStateOf("") }
    var showSecondaryNumber by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleAccountsDropdown by remember { mutableStateOf(false) }

    // Separate google destinations
    val googleDestinations = remember(destinations) {
        destinations.filter { it.type == ContactSaveDestinationType.GOOGLE }
    }

    Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = !isSaving
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Title + Close Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PurpleUltraLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = PurplePrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "New Contact",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Save to Phone, SIM, or Google",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // SAVE DESTINATION SECTION (Phone / SIM / Google)
                Text(
                    text = "SAVE CONTACT TO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Google Option
                    DestinationCard(
                        title = "Google",
                        subtitle = if (selectedDestination.type == ContactSaveDestinationType.GOOGLE) {
                            selectedDestination.accountName?.substringBefore("@") ?: "Cloud"
                        } else {
                            "Gmail"
                        },
                        icon = Icons.Default.Cloud,
                        iconTint = Color(0xFFEA4335),
                        isSelected = selectedDestination.type == ContactSaveDestinationType.GOOGLE,
                        hasDropdown = googleDestinations.size > 1,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (googleDestinations.size > 1) {
                                showGoogleAccountsDropdown = true
                            } else {
                                selectedDestination = googleDestinations.firstOrNull() ?: fallbackDestination
                            }
                        }
                    )

                    // Phone Option
                    val phoneDest = remember(destinations) {
                        destinations.firstOrNull { it.type == ContactSaveDestinationType.PHONE }
                            ?: ContactSaveDestination(
                                type = ContactSaveDestinationType.PHONE,
                                title = "Phone Storage",
                                subtitle = "Device Memory"
                            )
                    }
                    DestinationCard(
                        title = "Phone",
                        subtitle = "Device",
                        icon = Icons.Default.PhoneAndroid,
                        iconTint = PurplePrimary,
                        isSelected = selectedDestination.type == ContactSaveDestinationType.PHONE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDestination = phoneDest
                        }
                    )

                    // SIM Option
                    val simDest = remember(destinations) {
                        destinations.firstOrNull { it.type == ContactSaveDestinationType.SIM }
                            ?: ContactSaveDestination(
                                type = ContactSaveDestinationType.SIM,
                                title = "SIM Card",
                                subtitle = "SIM Memory"
                            )
                    }
                    DestinationCard(
                        title = "SIM",
                        subtitle = "Card",
                        icon = Icons.Default.SimCard,
                        iconTint = Color(0xFF059669),
                        isSelected = selectedDestination.type == ContactSaveDestinationType.SIM,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDestination = simDest
                        }
                    )
                }

                // Dropdown menu if multiple Google accounts exist
                if (googleDestinations.size > 1) {
                    DropdownMenu(
                        expanded = showGoogleAccountsDropdown,
                        onDismissRequest = { showGoogleAccountsDropdown = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        Text(
                            text = "Select Google Account",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        for (gDest in googleDestinations) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = gDest.accountName ?: "Google Account",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextDark
                                        )
                                        Text(
                                            text = "Sync with Google Cloud",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = Color(0xFFEA4335),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    selectedDestination = gDest
                                    showGoogleAccountsDropdown = false
                                }
                            )
                        }
                    }
                }

                // Active Destination Info Banner
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (selectedDestination.type) {
                        ContactSaveDestinationType.GOOGLE -> Color(0xFFFEE2E2).copy(alpha = 0.6f)
                        ContactSaveDestinationType.PHONE -> PurpleUltraLight
                        ContactSaveDestinationType.SIM -> Color(0xFFECFDF5)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (selectedDestination.type) {
                                ContactSaveDestinationType.GOOGLE -> Icons.Default.Cloud
                                ContactSaveDestinationType.PHONE -> Icons.Default.PhoneAndroid
                                ContactSaveDestinationType.SIM -> Icons.Default.SimCard
                            },
                            contentDescription = null,
                            tint = when (selectedDestination.type) {
                                ContactSaveDestinationType.GOOGLE -> Color(0xFFDC2626)
                                ContactSaveDestinationType.PHONE -> PurplePrimary
                                ContactSaveDestinationType.SIM -> Color(0xFF059669)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (selectedDestination.type) {
                                ContactSaveDestinationType.GOOGLE -> "Saving to Google: ${selectedDestination.accountName ?: "Cloud Sync"}"
                                ContactSaveDestinationType.PHONE -> "Saving to Phone Storage (Device Internal Memory)"
                                ContactSaveDestinationType.SIM -> "Saving to SIM Card (Direct SIM Storage)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message if any
                AnimatedVisibility(visible = !errorMessage.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
                    errorMessage?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = err,
                                fontSize = 12.sp,
                                color = Color(0xFFB91C1C),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("Contact Name *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. John Doe", color = TextLightGray, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (name.isNotBlank()) PurplePrimary else TextLightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            IconButton(onClick = { name = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextLightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedIndicatorColor = PurplePrimary,
                        unfocusedIndicatorColor = DividerColor
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Phone Number Field
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("Phone Number *", fontSize = 13.sp) },
                    placeholder = { Text("e.g. +880 1711 000000", color = TextLightGray, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = if (phoneNumber.isNotBlank()) PurplePrimary else TextLightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (phoneNumber.isNotEmpty()) {
                            IconButton(onClick = { phoneNumber = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextLightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedIndicatorColor = PurplePrimary,
                        unfocusedIndicatorColor = DividerColor
                    )
                )

                // Secondary Number (Toggle)
                if (showSecondaryNumber) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = secondaryNumber,
                        onValueChange = { secondaryNumber = it },
                        label = { Text("Secondary Phone (Optional)", fontSize = 13.sp) },
                        placeholder = { Text("Home / Work number", color = TextLightGray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = TextLightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                secondaryNumber = ""
                                showSecondaryNumber = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedIndicatorColor = PurplePrimary,
                            unfocusedIndicatorColor = DividerColor
                        )
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "+ Add another phone number",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PurplePrimary,
                            modifier = Modifier
                                .clickable { showSecondaryNumber = true }
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Email Field (Only for Phone & Google; SIM doesn't hold emails)
                if (selectedDestination.type != ContactSaveDestinationType.SIM) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address (Optional)", fontSize = 13.sp) },
                        placeholder = { Text("e.g. john@example.com", color = TextLightGray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = if (email.isNotBlank()) Color(0xFFEA4335) else TextLightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (email.isNotEmpty()) {
                                IconButton(onClick = { email = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = TextLightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedIndicatorColor = PurplePrimary,
                            unfocusedIndicatorColor = DividerColor
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Company / Organization Field
                    OutlinedTextField(
                        value = company,
                        onValueChange = { company = it },
                        label = { Text("Company / Organization (Optional)", fontSize = 13.sp) },
                        placeholder = { Text("e.g. Acme Corp", color = TextLightGray, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Business,
                                contentDescription = null,
                                tint = TextLightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedIndicatorColor = PurplePrimary,
                            unfocusedIndicatorColor = DividerColor
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Action Buttons (Save & Cancel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel", fontSize = 14.sp, color = TextDark)
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Please enter contact name."
                                return@Button
                            }
                            if (phoneNumber.isBlank()) {
                                errorMessage = "Please enter phone number."
                                return@Button
                            }
                            errorMessage = null
                            onSave(
                                name.trim(),
                                phoneNumber.trim(),
                                secondaryNumber.trim().ifBlank { null },
                                email.trim().ifBlank { null },
                                company.trim().ifBlank { null },
                                selectedDestination
                            )
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.3f).height(48.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Contact", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    hasDropdown: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) PurpleUltraLight else Color(0xFFF8FAFC),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) PurplePrimary else DividerColor
        ),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) PurplePrimary.copy(alpha = 0.15f) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) PurplePrimary else TextDark
                )
                if (hasDropdown) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = if (isSelected) PurplePrimary else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
