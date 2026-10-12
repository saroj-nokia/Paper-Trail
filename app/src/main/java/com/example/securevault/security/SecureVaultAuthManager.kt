package com.example.securevault.security

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.Cipher

class SecureVaultAuthManager(private val context: Context) {
  private val _isUnlocked = MutableStateFlow(false)
  val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

  @Volatile
  var activeCipher: Cipher? = null
    private set

  fun unlockDirectly(cipher: Cipher? = null) {
    activeCipher = cipher
    _isUnlocked.value = true
  }

  fun lock() {
    _isUnlocked.value = false
    activeCipher = null
  }

  fun canAuthenticateWithBiometrics(): Int {
    val biometricManager = BiometricManager.from(context)
    return biometricManager.canAuthenticate(
      BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
  }

  fun promptBiometric(
    activity: FragmentActivity,
    cipher: Cipher?,
    title: String = "Unlock SecureVault",
    subtitle: String = "Hardware-backed biometric authorization required to access encrypted files",
    onSuccess: (Cipher) -> Unit,
    onError: (String) -> Unit
  ) {
    if (cipher == null) {
      callOnErrorOnMain(activity, "Secure authentication could not start because the vault key is unavailable.", onError)
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        val authenticatedCipher = result.cryptoObject?.cipher
        if (authenticatedCipher == null) {
          lock()
          onError("Authentication did not return an authenticated cryptographic cipher.")
          return
        }
        activeCipher = authenticatedCipher
        _isUnlocked.value = true
        onSuccess(authenticatedCipher)
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        lock()
        onError(errString.toString())
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        onError("Biometric verification failed. Please try again.")
      }
    })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setNegativeButtonText("Cancel")
      .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
      .build()

    try {
      prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (e: Exception) {
      lock()
      onError("Failed to initiate biometric hardware verification: ${e.message}")
    }
  }

  private fun callOnErrorOnMain(activity: FragmentActivity, message: String, onError: (String) -> Unit) {
    ContextCompat.getMainExecutor(activity).execute {
      lock()
      onError(message)
    }
  }
}
