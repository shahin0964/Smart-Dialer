package com.smartdialer.app.ui.dialogs

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.smartdialer.app.data.repository.EncryptedBackupManager
import com.smartdialer.app.ui.theme.LocalAppTheme

@Composable
fun EncryptedBackupDialog(
    onDismiss: () -> Unit,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = EncryptedBackupManager.exportToBytes(context, password)
                EncryptedBackupManager.writeToUri(context, uri, bytes)
                isError = false
                statusMessage = "AES-256 encrypted backup saved successfully!"
            } catch (e: Exception) {
                isError = true
                statusMessage = e.message ?: "Backup failed"
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = EncryptedBackupManager.readFromUri(context, uri)
                val summary = EncryptedBackupManager.restoreFromBytes(context, bytes, password)
                isError = false
                statusMessage = summary
                onRestored()
            } catch (e: Exception) {
                isError = true
                statusMessage = "Wrong password or invalid backup file"
            }
        }
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
                    title = "AES-256 Encrypted Backup",
                    icon = Icons.Default.EnhancedEncryption,
                    iconTint = Color(0xFF10B981),
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Protect all SIP accounts, call notes, blocked numbers, speed dials, and app settings with AES-256-GCM encryption.",
                    fontSize = 12.sp,
                    color = theme.textMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Encryption Password (min 4 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
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

                if (!statusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isError) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isError) Color(0xFFDC2626) else Color(0xFF16A34A),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (password.length < 4) {
                            Toast.makeText(context, "Enter a password of at least 4 characters", Toast.LENGTH_SHORT).show()
                        } else {
                            saveLauncher.launch("SmartDialer_Backup_${System.currentTimeMillis()}.sdbackup")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Encrypted Backup File", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (password.length < 4) {
                                Toast.makeText(context, "Enter your backup password first", Toast.LENGTH_SHORT).show()
                            } else {
                                openLauncher.launch(arrayOf("*/*"))
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.primary)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = theme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restore", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                    }

                    OutlinedButton(
                        onClick = {
                            if (password.length < 4) {
                                Toast.makeText(context, "Enter a password first", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    val bytes = EncryptedBackupManager.exportToBytes(context, password)
                                    val file = EncryptedBackupManager.createTempShareFile(context, bytes)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share Encrypted Backup"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Share failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, theme.divider)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = theme.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    }
                }
            }
        }
    }
}
