package com.smartdialer.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartdialer.app.data.repository.IpCallPreferences
import com.smartdialer.app.ui.settings.IpAccountSettingsScreen

@Composable
fun IpCallConfigDialog(
    ipPreferences: IpCallPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        IpAccountSettingsScreen(
            ipPreferences = ipPreferences,
            onBack = {
                onSaved()
                onDismiss()
            }
        )
    }
}
