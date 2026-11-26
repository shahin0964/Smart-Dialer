package com.smartdialer.app.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartdialer.app.data.model.SimInfo
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.theme.LocalAppTheme
import com.smartdialer.app.ui.theme.Sim1Blue
import com.smartdialer.app.ui.theme.Sim2Green

@Composable
fun CallForwardingDialog(
    sims: List<SimInfo>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    var selectedSimSlot by remember { mutableIntStateOf(0) }
    var forwardNumber by remember { mutableStateOf("") }

    fun sendMmiCode(code: String) {
        CallManager.initiateCall(context, code, selectedSimSlot)
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = theme.cardBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                DialogHeader(
                    title = "Call Forwarding (SIM)",
                    icon = Icons.Default.PhoneForwarded,
                    iconTint = Sim1Blue,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Select SIM & enter target number to configure carrier call forwarding via GSM codes.",
                    fontSize = 12.sp,
                    color = theme.textMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 1).forEach { slot ->
                        val sim = sims.firstOrNull { it.slotIndex == slot }
                        val label = sim?.displayName ?: "SIM ${slot + 1}"
                        val active = selectedSimSlot == slot
                        val accent = if (slot == 0) Sim1Blue else Sim2Green
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) accent.copy(alpha = 0.16f) else theme.surface,
                            border = BorderStroke(1.5.dp, if (active) accent else theme.divider),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedSimSlot = slot }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SIM ${slot + 1}: $label",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) accent else theme.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = forwardNumber,
                    onValueChange = { forwardNumber = it },
                    label = { Text("Forward to Number") },
                    placeholder = { Text("e.g. 017XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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

                Spacer(modifier = Modifier.height(12.dp))

                val options = listOf(
                    "*21*" to "Always Forward (*21*)",
                    "**67*" to "When Busy (**67*)",
                    "**61*" to "When Unanswered (**61*)",
                    "**62*" to "When Unreachable (**62*)"
                )

                options.forEach { (prefix, label) ->
                    Surface(
                        onClick = {
                            val clean = forwardNumber.filter { it.isDigit() || it == '+' }.trim()
                            if (clean.length < 5) {
                                Toast.makeText(context, "Enter a valid destination number", Toast.LENGTH_SHORT).show()
                            } else {
                                sendMmiCode("$prefix$clean#")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, theme.divider),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { sendMmiCode("##002#") },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disable All Forwarding (##002#)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            }
        }
    }
}
