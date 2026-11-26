package com.smartdialer.app.utils

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * Dependency-free Biometric / Device PIN Lock helper using platform BiometricPrompt (API 28+).
 */
object BiometricLockHelper {

    fun isDeviceAuthenticationAvailable(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure == true) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bm = context.getSystemService(BiometricManager::class.java)
            @Suppress("DEPRECATION")
            val result = bm?.canAuthenticate()
            if (result == BiometricManager.BIOMETRIC_SUCCESS) return true
        }
        return false
    }

    fun authenticate(
        context: Context,
        title: String = "Smart Dialer Locked",
        subtitle: String = "Verify fingerprint, face, or screen lock to continue",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isDeviceAuthenticationAvailable(context)) {
            // No lock configured on device: allow seamless access
            onSuccess()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val builder = BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle(subtitle)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }

                val prompt = builder.build()
                val executor = ContextCompat.getMainExecutor(context)
                val cancelSignal = CancellationSignal()

                prompt.authenticate(
                    cancelSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onError(errString?.toString() ?: "Authentication cancelled")
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                        }
                    }
                )
            } catch (e: Exception) {
                onError(e.message ?: "Biometric prompt unavailable")
            }
        } else {
            onSuccess()
        }
    }
}
