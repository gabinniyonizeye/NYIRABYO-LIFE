package com.example.nyirabyolife.security

import android.content.Context
import android.os.Build
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.nyirabyolife.R
import com.example.nyirabyolife.data.local.PreferencesManager

class RecoveryAuthManager(
    private val activity: FragmentActivity,
    private val onSuccess: () -> Unit,
    private val onFailed: (String) -> Unit = {}
) {

    private val prefs = PreferencesManager.getInstance(activity)

    fun authenticate() {
        val biometricManager = BiometricManager.from(activity)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (prefs.isBiometricEnabled() && canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            promptBiometric()
        } else {
            promptPinFallback()
        }
    }

    private fun promptBiometric() {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication Required")
            .setSubtitle("Verify identity to open private recovery area")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onFailed(errString.toString())
                } else {
                    promptPinFallback()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        })

        biometricPrompt.authenticate(promptInfo)
    }

    private fun promptPinFallback() {
        if (!prefs.hasPin()) {
            // First time setup: allow setting up a 4-digit PIN
            promptSetupPin()
            return
        }

        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-digit PIN"
            setPadding(48, 32, 48, 32)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(activity, R.color.text_tertiary))
        }

        AlertDialog.Builder(activity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Private Area Lock")
            .setMessage("Enter your secret PIN to access recovery records:")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = input.text.toString().trim()
                if (prefs.verifyPin(entered)) {
                    onSuccess()
                } else {
                    Toast.makeText(activity, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    onFailed("Incorrect PIN")
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onFailed("Cancelled")
            }
            .show()
    }

    private fun promptSetupPin() {
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Choose 4-digit PIN (e.g. 1234)"
            setPadding(48, 32, 48, 32)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(activity, R.color.text_tertiary))
        }

        AlertDialog.Builder(activity, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Dialog_Alert)
            .setTitle("Set Recovery PIN")
            .setMessage("Protect your private journal and recovery records with a personal PIN:")
            .setView(input)
            .setPositiveButton("Set & Unlock") { _, _ ->
                val entered = input.text.toString().trim()
                if (entered.length >= 4) {
                    prefs.setPin(entered)
                    Toast.makeText(activity, "PIN configured successfully", Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    Toast.makeText(activity, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    onFailed("PIN too short")
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onFailed("Cancelled")
            }
            .show()
    }
}
