package com.smartdialer.app.ui.call

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.smartdialer.app.data.model.CallStatus
import com.smartdialer.app.telecom.CallManager
import com.smartdialer.app.ui.theme.SmartDialerTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show when locked & turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        lifecycleScope.launch {
            CallManager.currentCallState.collectLatest { state ->
                if (state.status == CallStatus.DISCONNECTED) {
                    finishAndRemoveTask()
                }
            }
        }

        setContent {
            SmartDialerTheme(darkTheme = true) {
                OngoingCallScreen(
                    onDismiss = { finishAndRemoveTask() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
