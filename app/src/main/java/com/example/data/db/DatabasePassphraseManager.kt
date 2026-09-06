package com.example.data.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.security.KeystoreCipherProvider
import com.example.security.KeystoreUnavailableException
import java.security.SecureRandom

object DatabasePassphraseManager {
  private const val TAG = "DatabasePassphraseMgr"

  // Modern SharedPreferences configuration using KeystoreCipherProvider
  const val PREFS_FILE = "papertrail_database_passphrase_prefs"
  const val KEYSTORE_KEY_ALIAS = "db_passphrase_key"
  private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_db_passphrase"

  @Synchronized
  fun getOrCreatePassphrase(context: Context): ByteArray {
    val plainPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // 1. Check if passphrase already exists in modern plain SharedPreferences (encrypted via Keystore)
    val encryptedPassphrase = plainPrefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
    if (encryptedPassphrase != null) {
      try {
        val decrypted = KeystoreCipherProvider.decryptString(KEYSTORE_KEY_ALIAS, encryptedPassphrase)
        return Base64.decode(decrypted, Base64.NO_WRAP)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to decrypt database passphrase with KeystoreCipherProvider: ${e.message}", e)
        throw KeystoreUnavailableException(
          "Failed to decrypt database passphrase: hardware security module unavailable or malfunctioning.",
          e
        )
      }
    }

    // 2. Generate new random 256-bit (32-byte) key
    val random = SecureRandom()
    val newKey = ByteArray(32)
    random.nextBytes(newKey)
    val encoded = Base64.encodeToString(newKey, Base64.NO_WRAP)

    return storePassphrase(plainPrefs, encoded)
  }

  private fun storePassphrase(
    plainPrefs: SharedPreferences,
    passphraseBase64: String
  ): ByteArray {
    try {
      val encrypted = KeystoreCipherProvider.encryptString(KEYSTORE_KEY_ALIAS, passphraseBase64)
      plainPrefs.edit().putString(KEY_ENCRYPTED_PASSPHRASE, encrypted).apply()
      return Base64.decode(passphraseBase64, Base64.NO_WRAP)
    } catch (e: Exception) {
      Log.e(TAG, "KeystoreCipherProvider encryption failed: ${e.message}", e)
      throw KeystoreUnavailableException(
        "Failed to encrypt database passphrase: hardware security module unavailable or malfunctioning.",
        e
      )
    }
  }
}
