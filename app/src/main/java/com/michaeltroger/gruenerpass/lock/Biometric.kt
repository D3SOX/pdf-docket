package com.michaeltroger.gruenerpass.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.michaeltroger.gruenerpass.R

private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

fun Context.canAuthenticate(): Boolean =
    BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

fun FragmentActivity.authenticate(
    onSuccess: () -> Unit,
    onError: () -> Unit = {},
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onUserInteraction()
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError()
        }
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.authenticate))
        .setConfirmationRequired(false)
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(promptInfo)
}
