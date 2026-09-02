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
    cipher: Cipher? = null,
    title: String = "Unlock SecureVault",
    subtitle: String = "Hardware-backed biometric authorization required to access encrypted files",
    onSuccess: (Cipher?) -> Unit,
    onError: (String) -> Unit
  ) {
    val executor = ContextCompat.getMainExecutor(activity)

    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        val authenticatedCipher = result.cryptoObject?.cipher ?: cipher
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

    if (cipher != null) {
      val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

      try {
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        return
      } catch (e: Exception) {
        Log.w("SecureVaultAuth", "Biometric CryptoObject authentication initiation failed: ${e.message}. Using standard prompt.")
      }
    }

    val fallbackPromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
      )
      .build()

    prompt.authenticate(fallbackPromptInfo)
  }
}
