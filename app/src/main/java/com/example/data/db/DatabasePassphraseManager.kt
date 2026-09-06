package com.example.data.db

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.security.KeystoreCipherProvider
import java.io.File
import java.security.SecureRandom

object DatabasePassphraseManager {
  private const val TAG = "DatabasePassphraseMgr"

  // Legacy SharedPreferences configuration (for transparent one-time migration)
  private const val LEGACY_PREFS_FILE = "papertrail_secure_vault_prefs"
  private const val LEGACY_KEY_DB_PASSPHRASE = "vault_db_encryption_key_v1"

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

    // 3. Migrate from legacy EncryptedSharedPreferences if present
    val migratedPassphrase = tryMigrateFromLegacyPrefs(context)
    if (migratedPassphrase != null) {
      return storePassphrase(plainPrefs, fallbackPrefs, migratedPassphrase)
    }

    // 4. Generate new random 256-bit (32-byte) key
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

  @Suppress("DEPRECATION")
  private fun tryMigrateFromLegacyPrefs(context: Context): String? {
    val legacyPrefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_FILE.xml")
    val legacyFallbackFile = File(context.applicationInfo.dataDir, "shared_prefs/${LEGACY_PREFS_FILE}_fallback.xml")

    // Check legacy fallback file first
    if (legacyFallbackFile.exists()) {
      try {
        val legacyFallbackPrefs = context.getSharedPreferences(LEGACY_PREFS_FILE + "_fallback", Context.MODE_PRIVATE)
        val oldFallbackValue = legacyFallbackPrefs.getString(LEGACY_KEY_DB_PASSPHRASE, null)
        if (oldFallbackValue != null) {
          legacyFallbackPrefs.edit().clear().apply()
          deleteSharedPrefsFile(context, LEGACY_PREFS_FILE + "_fallback", legacyFallbackFile)
          Log.i(TAG, "Migrated legacy fallback passphrase.")
          return oldFallbackValue
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error checking legacy fallback file: ${e.message}")
      }
    }

    if (!legacyPrefsFile.exists()) {
      return null
    }

    return try {
      val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

      val oldEncryptedPrefs = EncryptedSharedPreferences.create(
        context,
        LEGACY_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )

      val legacyValue = oldEncryptedPrefs.getString(LEGACY_KEY_DB_PASSPHRASE, null)
      if (legacyValue != null) {
        oldEncryptedPrefs.edit().clear().apply()
        deleteSharedPrefsFile(context, LEGACY_PREFS_FILE, legacyPrefsFile)
        Log.i(TAG, "Successfully migrated database passphrase from legacy EncryptedSharedPreferences.")
        legacyValue
      } else {
        deleteSharedPrefsFile(context, LEGACY_PREFS_FILE, legacyPrefsFile)
        null
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read legacy EncryptedSharedPreferences: ${e.message}")
      null
    }
  }

  private fun deleteSharedPrefsFile(context: Context, name: String, file: File) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.deleteSharedPreferences(name)
      } else {
        file.delete()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to delete legacy shared preferences '$name': ${e.message}")
      file.delete()
    }
  }
}
