package com.victor.postly.security

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.widget.Toast
import com.victor.postly.R

object AppUnlockHelper {
    private val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun requireUnlock(
        activity: Activity,
        onUnlocked: () -> Unit,
        onCanceled: () -> Unit = {}
    ) {
        if (AppUnlockManager.isUnlocked()) {
            onUnlocked()
            return
        }

        val biometricManager = activity.getSystemService(BiometricManager::class.java)
        val canAuthenticate = biometricManager?.canAuthenticate(AUTHENTICATORS)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                activity,
                activity.getString(R.string.app_unlock_unavailable),
                Toast.LENGTH_LONG
            ).show()
            AppUnlockManager.markUnlocked()
            onUnlocked()
            return
        }

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.app_unlock_title))
            .setSubtitle(activity.getString(R.string.app_unlock_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppUnlockManager.markUnlocked()
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.app_unlock_error),
                        Toast.LENGTH_SHORT
                    ).show()
                    onCanceled()
                }
            }
        )
    }
}
