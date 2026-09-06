package com.example.data.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.security.KeystoreCipherProvider
import java.security.SecureRandom

object DatabasePassphraseManager {
  private const val TAG = "DatabasePassphraseMgr"

  // Modern SharedPreferences configuration using KeystoreCipherProvider
  const val PREFS_FILE = "papertrail_database_passphrase_prefs"
  const val PREFS_FALLBACK_FILE = "papertrail_database_passphrase_fallback_prefs"
  const val KEYSTORE_KEY_ALIAS = "db_passphrase_key"
  private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_db_passphrase"
  private const val KEY_FALLBACK_PASSPHRASE = "vault_db_encryption_key_v1"

  @Volatile
  var isFallbackMode: Boolean = false
    private set

  @Synchronized
  fun getOrCreatePassphrase(context: Context): ByteArray {
    val plainPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // 1. Check if passphrase already exists in modern plain SharedPreferences (encrypted via Keystore)
    val encryptedPassphrase = plainPrefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
    if (encryptedPassphrase != null) {
      try {
        val decrypted = KeystoreCipherProvider.decryptString(KEYSTORE_KEY_ALIAS, encryptedPassphrase)
        isFallbackMode = false
        return Base64.decode(decrypted, Base64.NO_WRAP)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to decrypt passphrase with KeystoreCipherProvider: ${e.message}. Checking fallback storage.")
      }
    }

    // 2. Check if a plaintext fallback passphrase was previously stored
    val fallbackPrefs = context.getSharedPreferences(PREFS_FALLBACK_FILE, Context.MODE_PRIVATE)
    val fallbackPassphrase = fallbackPrefs.getString(KEY_FALLBACK_PASSPHRASE, null)
    if (fallbackPassphrase != null) {
      // Try to upgrade fallback to Keystore encryption if Keystore is now available
      try {
        val encrypted = KeystoreCipherProvider.encryptString(KEYSTORE_KEY_ALIAS, fallbackPassphrase)
        plainPrefs.edit().putString(KEY_ENCRYPTED_PASSPHRASE, encrypted).apply()
        fallbackPrefs.edit().remove(KEY_FALLBACK_PASSPHRASE).apply()
        isFallbackMode = false
        Log.i(TAG, "Upgraded fallback database passphrase to Keystore-backed encryption.")
        return Base64.decode(fallbackPassphrase, Base64.NO_WRAP)
      } catch (e: Exception) {
        isFallbackMode = true
        return Base64.decode(fallbackPassphrase, Base64.NO_WRAP)
      }
    }

    // 3. Generate new random 256-bit (32-byte) key
    val random = SecureRandom()
    val newKey = ByteArray(32)
    random.nextBytes(newKey)
    val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)

    return storePassphrase(plainPrefs, fallbackPrefs, encoded)
  }

  private fun storePassphrase(
    plainPrefs: SharedPreferences,
    fallbackPrefs: SharedPreferences,
    passphraseBase64: String
  ): ByteArray {
    return try {
      val encrypted = KeystoreCipherProvider.encryptString(KEYSTORE_KEY_ALIAS, passphraseBase64)
      plainPrefs.edit().putString(KEY_ENCRYPTED_PASSPHRASE, encrypted).apply()
      fallbackPrefs.edit().remove(KEY_FALLBACK_PASSPHRASE).apply()
      isFallbackMode = false
      Base64.decode(passphraseBase64, Base64.NO_WRAP)
    } catch (e: Exception) {
      Log.w(TAG, "KeystoreCipherProvider encryption failed: ${e.message}. Using private SharedPreferences fallback.")
      isFallbackMode = true
      fallbackPrefs.edit().putString(KEY_FALLBACK_PASSPHRASE, passphraseBase64).apply()
      Base64.decode(passphraseBase64, Base64.NO_WRAP)
    }
  }
}
