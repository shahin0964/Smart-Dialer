package com.smartdialer.app.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartdialer.app.data.model.EndedCallSummary
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.data.repository.CallNotesRepository
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.components.ContactAvatar
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.CallMissedRed
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.utils.SmsHelper
import com.smartdialer.app.utils.WhatsAppHelper

@Composable
fun PostCallSummaryDialog(
    summary: EndedCallSummary,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val existingNote = remember(summary.number) {
        CallNotesRepository.getNoteForNumber(context, summary.number)
    }
    var noteText by remember(summary.number) { mutableStateOf(existingNote?.note ?: "") }
    var selectedTag by remember(summary.number) { mutableStateOf(existingNote?.tag ?: "General") }
    var noteSaved by remember { mutableStateOf(false) }

    val tags = listOf("General", "Business", "Delivery", "Important")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = theme.primaryUltraLight
                        ) {
                            Text(
                                text = "Post-Call Summary",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Caller Summary Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(
                            name = summary.displayTitle,
                            photoUri = summary.photoUri,
                            size = 52.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = summary.displayTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (summary.contactName.isNotBlank() && summary.contactName != summary.number) {
                                Text(
                                    text = summary.number,
                                    fontSize = 13.sp,
                                    color = theme.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val callLabel = when {
                                !summary.wasAnswered && summary.isIncoming -> "Missed / Declined"
                                summary.isIncoming -> "Incoming • ${summary.durationFormatted}"
                                else -> "Outgoing • ${summary.durationFormatted}"
                            }
                            val typeBadge = if (summary.isIpCall) "IP Call • $callLabel" else callLabel
                            Text(
                                text = typeBadge,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!summary.wasAnswered && summary.isIncoming) CallMissedRed else CallIncomingGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                if (summary.isIpCall) {
                                    CallManager.initiateIpCall(context, summary.number)
                                } else {
                                    CallManager.initiateCall(context, summary.number, 0)
                                }
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CallIncomingGreen)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Call Back", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            onClick = {
                                WhatsAppHelper.openWhatsAppChat(context, summary.number)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF16A34A), modifier = Modifier.size(19.dp))
                            }
                        }

                        Surface(
                            onClick = {
                                SmsHelper.openSms(context, summary.number)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, theme.divider),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Message, contentDescription = "SMS", tint = theme.primary, modifier = Modifier.size(19.dp))
                            }
                        }

                        Surface(
                            onClick = {
                                AppSettingsPreferences(context).addBlockedNumber(summary.number)
                                Toast.makeText(context, "Blocked ${summary.number}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Block, contentDescription = "Block", tint = Color(0xFFDC2626), modifier = Modifier.size(19.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Call Note Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EditNote, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Call Note (নোট লিখে রাখুন)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tags) { tag ->
                            val selected = selectedTag == tag
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) theme.primary else theme.surface,
                                border = BorderStroke(1.dp, if (selected) theme.primary else theme.divider),
                                modifier = Modifier.clickable { selectedTag = tag }
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else theme.textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            noteSaved = false
                        },
                        placeholder = { Text("e.g. Delivery parcel, client follow-up...", fontSize = 13.sp, color = theme.textMuted) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = theme.surface,
                            unfocusedContainerColor = theme.surface,
                            focusedTextColor = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            focusedIndicatorColor = theme.primary,
                            unfocusedIndicatorColor = theme.divider
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            CallNotesRepository.saveNote(context, summary.number, noteText, selectedTag)
                            noteSaved = true
                            Toast.makeText(context, "Call note saved", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, theme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = theme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (noteSaved) "Note Saved!" else "Save Call Note",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Callback Reminder Section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Remind Me to Call Back",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val options = listOf(15 to "15m", 30 to "30m", 60 to "1h", 180 to "3h")
                        for ((mins, label) in options) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surface,
                                border = BorderStroke(1.dp, theme.divider),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (noteText.isNotBlank()) {
                                            CallNotesRepository.saveNote(context, summary.number, noteText, selectedTag)
                                        }
                                        CallNotesRepository.scheduleReminder(
                                            context = context,
                                            number = summary.number,
                                            displayName = summary.displayTitle,
                                            delayMinutes = mins,
                                            note = noteText
                                        )
                                        Toast.makeText(context, "Callback reminder set for $label", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = theme.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (noteText.isNotBlank() && !noteSaved) {
                                CallNotesRepository.saveNote(context, summary.number, noteText, selectedTag)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
