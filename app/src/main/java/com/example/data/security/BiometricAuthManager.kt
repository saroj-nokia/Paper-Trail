package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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

  private fun getOrCreateAppLockKey(useStrongBox: Boolean = true): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (keyStore.containsAlias(KEY_ALIAS_APP_LOCK)) {
      val entry = keyStore.getEntry(KEY_ALIAS_APP_LOCK, null) as? KeyStore.SecretKeyEntry
      if (entry?.secretKey != null) {
        return entry.secretKey
      }
    }

    if (Build.FINGERPRINT == "robolectric") {
      val kgen = KeyGenerator.getInstance("AES")
      kgen.init(256)
      return kgen.generateKey()
    }

    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      ANDROID_KEYSTORE
    )

    val builder = KeyGenParameterSpec.Builder(
      KEY_ALIAS_APP_LOCK,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setUserAuthenticationRequired(true)
      .setInvalidatedByBiometricEnrollment(true)
      .setUserAuthenticationParameters(
        0,
        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
      )

    if (useStrongBox) {
      try {
        builder.setIsStrongBoxBacked(true)
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
      } catch (e: StrongBoxUnavailableException) {
        Log.w(TAG, "StrongBox unavailable for app lock key, falling back to TEE: ${e.message}")
        return getOrCreateAppLockKey(useStrongBox = false)
      } catch (e: Exception) {
        Log.w(TAG, "StrongBox key initialization failed for app lock: ${e.message}. Falling back to TEE.")
        return getOrCreateAppLockKey(useStrongBox = false)
      }
    }

    keyGenerator.init(builder.build())
    return keyGenerator.generateKey()
  }

  private fun deleteAppLockKey() {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      if (keyStore.containsAlias(KEY_ALIAS_APP_LOCK)) {
        keyStore.deleteEntry(KEY_ALIAS_APP_LOCK)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete app lock key: ${e.message}")
    }
  }

  private fun initAppLockEncryptCipher(retryOnInvalidation: Boolean = true): Cipher? {
    return try {
      val key = getOrCreateAppLockKey(useStrongBox = true)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, key)
      cipher
    } catch (e: Exception) {
      if (e is KeyPermanentlyInvalidatedException || e.cause is KeyPermanentlyInvalidatedException) {
        if (retryOnInvalidation) {
          Log.i(TAG, "App lock key permanently invalidated. Regenerating key and retrying cipher init.")
          deleteAppLockKey()
          initAppLockEncryptCipher(retryOnInvalidation = false)
        } else {
          Log.e(TAG, "App lock key remains invalidated after regeneration.", e)
          null
        }
      } else {
        Log.e(TAG, "Failed to initialize app lock encrypt cipher: ${e.message}", e)
        null
      }
    }
  }

  fun promptBiometric(
    activity: FragmentActivity,
    title: String = "Unlock Paper Trail",
    subtitle: String = "Confirm fingerprint, face, or device PIN to access your vault",
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    val cipher = initAppLockEncryptCipher()
    if (cipher == null) {
      ContextCompat.getMainExecutor(activity).execute {
        onError("Secure authentication is unavailable on this device.")
      }
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        val authenticatedCipher = result.cryptoObject?.cipher
        if (authenticatedCipher == null) {
          onError("Authentication did not return a cryptographic confirmation.")
          return
        }

        try {
          // Cryptographic proof that hardware authentication succeeded
          authenticatedCipher.doFinal(AUTH_CONFIRMATION_PAYLOAD)
          _isUnlocked.value = true
          onSuccess()
        } catch (e: Exception) {
          Log.e(TAG, "Cryptographic confirmation failed: ${e.message}", e)
          _isUnlocked.value = false
          onError("Cryptographic authentication failed. Please try again.")
        }
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

    try {
      prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch BiometricPrompt with CryptoObject: ${e.message}", e)
      onError("Secure authentication could not start: ${e.message}")
    }
  }

  companion object {
    private const val TAG = "BiometricAuthManager"
    private const val KEY_LOCK_ENABLED = "biometric_lock_enabled"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_APP_LOCK = "papertrail_applock_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val AUTH_CONFIRMATION_PAYLOAD = "paper-trail-unlock".toByteArray(Charsets.UTF_8)
  }
}

