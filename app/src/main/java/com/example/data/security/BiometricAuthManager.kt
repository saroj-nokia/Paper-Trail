package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BiometricAuthManager(private val context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("papertrail_security_settings", Context.MODE_PRIVATE)

  private val _isUnlocked = MutableStateFlow(false)
  val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

  val isLockConfigured: Boolean
    get() = prefs.getBoolean(KEY_LOCK_ENABLED, true)

  fun setLockConfigured(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    if (!enabled) {
      _isUnlocked.value = true
    }
  }

  fun canAuthenticateWithBiometrics(): Int {
    val biometricManager = BiometricManager.from(context)
    return biometricManager.canAuthenticate(
      BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
  }

  fun lock() {
    if (isLockConfigured) {
      _isUnlocked.value = false
    }
  }

  fun promptBiometric(
    activity: FragmentActivity,
    title: String = "Unlock Paper Trail",
    subtitle: String = "Confirm fingerprint, face, or device PIN to access your vault",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val executor = ContextCompat.getMainExecutor(activity)

    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        _isUnlocked.value = true
        onSuccess()
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        onError(errString.toString())
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        onError("Authentication failed. Please try again.")
      }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
      )
      .build()

    prompt.authenticate(promptInfo)
  }

  companion object {
    private const val KEY_LOCK_ENABLED = "biometric_lock_enabled"
  }
}
