package com.smartdialer.app.ui.dialer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.smartdialer.app.ui.components.IpCallConfigDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.smartdialer.app.data.repository.AppSettingsPreferences
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.app.data.model.ContactItem
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.theme.BgLight
import com.smartdialer.app.ui.theme.IpCallPurple
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.Sim1Blue
import com.smartdialer.app.ui.theme.Sim2Green
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    viewModel: DialerViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val inputNumber by viewModel.inputNumber.collectAsState()
    val matchedContacts by viewModel.matchedContacts.collectAsState()
    val showIpDialog by viewModel.showIpDialog.collectAsState()
    val appSettings: AppSettingsPreferences = remember { AppSettingsPreferences(context) }

    val pasteFromClipboard = {
        val clipData = clipboardManager.getText()?.text
        if (!clipData.isNullOrBlank()) {
            val clean = clipData.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
            if (clean.isNotEmpty()) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                viewModel.setNumber(inputNumber + clean)
            }
        }
    }

    if (showIpDialog) {
        IpCallConfigDialog(
            ipPreferences = viewModel.ipPreferences,
            onDismiss = { viewModel.dismissIpDialog() },
            onSaved = {
                if (inputNumber.isNotBlank()) {
                    viewModel.makeIpCall(context)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgLight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Number Display Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { },
                        onLongClick = { pasteFromClipboard() }
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (inputNumber.isEmpty()) {
                        Text(
                            text = "Enter number...",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )

                        // Quick Paste Button when empty
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { pasteFromClipboard() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = PurplePrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Paste",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurplePrimary
                            )
                        }
                    } else {
                        Text(
                            text = inputNumber,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Paste Icon Button
                            IconButton(
                                onClick = { pasteFromClipboard() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Backspace / Clear All Button (Single click = 1 digit, Long press = Clear all)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .combinedClickable(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.onBackspaceClick()
                                        },
                                        onLongClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            viewModel.onClearAll()
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace (Long press to clear all)",
                                    tint = TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Real T9 Contact Matches if any
            if (matchedContacts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(matchedContacts) { contact ->
                        ContactChip(contact = contact) {
                            viewModel.setNumber(contact.phoneNumber)
                        }
                    }
                }
            }
        }

        // 3x4 Dialpad Keypad Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: 1, 2, 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KeypadButton(
                    digit = "1",
                    subtext = "o_o",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("1")
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val vm = appSettings.voicemailNumber
                        if (vm.isNotBlank()) {
                            CallManager.initiateCall(context, vm, 0)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "2",
                    subtext = "ABC",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("2")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(2)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "3",
                    subtext = "DEF",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("3")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(3)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
            }

            // Row 2: 4, 5, 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KeypadButton(
                    digit = "4",
                    subtext = "GHI",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("4")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(4)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "5",
                    subtext = "JKL",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("5")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(5)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "6",
                    subtext = "MNO",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("6")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(6)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
            }

            // Row 3: 7, 8, 9
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KeypadButton(
                    digit = "7",
                    subtext = "PQRS",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("7")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(7)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "8",
                    subtext = "TUV",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("8")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(8)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "9",
                    subtext = "WXYZ",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("9")
                    },
                    onLongClick = {
                        val sd = appSettings.getSpeedDial(9)
                        if (sd != null && sd.first.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            CallManager.initiateCall(context, sd.first, 0)
                        }
                    }
                )
            }

            // Row 4: *, 0, #
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                KeypadButton(
                    digit = "*",
                    subtext = "",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("*")
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "0",
                    subtext = "+",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("0")
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.onZeroLongClick()
                    }
                )
                Spacer(modifier = Modifier.width(14.dp))
                KeypadButton(
                    digit = "#",
                    subtext = "",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        viewModel.onDigitClick("#")
                    }
                )
            }
        }

        // Bottom Action Call Buttons: SIM 1, SIM 2, IP Call
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SIM 1 (Blue)
            CallPillButton(
                title = "SIM 1",
                icon = Icons.Default.SimCard,
                backgroundColor = Sim1Blue,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.callWithSim(context, 0)
                }
            )

            // SIM 2 (Green)
            CallPillButton(
                title = "SIM 2",
                icon = Icons.Default.SimCard,
                backgroundColor = Sim2Green,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.callWithSim(context, 1)
                }
            )

            // IP Call (Purple)
            CallPillButton(
                title = "IP Call",
                icon = Icons.Default.Language,
                backgroundColor = IpCallPurple,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.makeIpCall(context)
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    viewModel.openIpSettings()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadButton(
    digit: String,
    subtext: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        color = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                if (subtext.isNotEmpty()) {
                    Text(
                        text = subtext,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextLightGray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallPillButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ContactChip(
    contact: ContactItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PurpleUltraLight,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = PurplePrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = contact.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PurplePrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.phoneNumber.isNotBlank()) {
                    Text(
                        text = contact.phoneNumber,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
