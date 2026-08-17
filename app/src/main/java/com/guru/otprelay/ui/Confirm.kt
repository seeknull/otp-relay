package com.guru.otprelay.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for the phone's own unlock — fingerprint, face, or the PIN it falls back to — before a
 * number is allowed to receive codes.
 *
 * Adding a contact is the moment that matters: everything afterwards only forwards to a number
 * already on the list, so guarding this one step covers the rest. It also means someone holding
 * your unlocked phone for a moment cannot quietly add their own number.
 *
 * A phone with no lock screen at all has nothing to ask, so the action goes ahead. Refusing would
 * make the app unusable on such a device without making anything safer.
 */
private const val ALLOWED = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

fun confirmIdentity(context: Context, subtitle: String, onConfirmed: () -> Unit) {
    val activity = context as? FragmentActivity ?: return onConfirmed()

    if (BiometricManager.from(context).canAuthenticate(ALLOWED) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) return onConfirmed()

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onConfirmed()
            }
            // A failed or cancelled check simply does not add the contact; saying so would only
            // repeat what the system prompt already showed.
        },
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm it is you")
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED)
            .build()
    )
}
