package io.vault.mobile.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthenticator(private val context: Context) {

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        // Strictly require BIOMETRIC_STRONG OR DEVICE_CREDENTIAL (fallback)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    android.util.Log.d("BiometricAuthenticator", "Authentication Succeeded")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    android.util.Log.e("BiometricAuthenticator", "Authentication Error: $errorCode - $errString")
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    android.util.Log.w("BiometricAuthenticator", "Authentication Failed (Recognition)")
                    onError("Authentication failed (fingerprint not recognized)")
                }
            })

        val allowedAuthenticators = if (cryptoObject != null) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+: Use the modern unified authenticator selection
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            // API 26-29: Use the legacy approach
            // Note: On some older devices, combining BIOMETRIC and DEVICE_CREDENTIAL in one line is flaky.
            // We prioritize the modern library's attempt but provide a negative button if device credential isn't guaranteed.
            if (cryptoObject == null) {
                // If no cryptoObject, we can use the library's device credential fallback
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            } else {
                // With cryptoObject (Fingerprint key), many older versions require a negative button
                builder.setNegativeButtonText("Cancel")
            }
        }

        val promptInfo = builder.build()

        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
