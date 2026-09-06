package com.example.securevault.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.BuildConfig
import com.example.security.KeystoreCipherProvider
import com.example.security.KeystoreUnavailableException
import com.example.securevault.logging.CryptoLogger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class VaultUpdateType {
  NONE,
  APP_UPDATE,
  OS_UPDATE
}

enum class MasterCredentialType(val title: String, val minLength: Int) {
  PIN("Master PIN", 6),
  PASSPHRASE("Master Passphrase", 6)
}

data class BiometricRosterState(
  val isKeyInvalidated: Boolean,
  val updateType: VaultUpdateType,
  val alertMessage: String,
  val isRogueAlert: Boolean,
  val credentialType: MasterCredentialType = MasterCredentialType.PIN,
  val hasMasterCredential: Boolean = true
)

sealed class MasterCredentialVerifyResult {
  object Success : MasterCredentialVerifyResult()
  data class LockedOut(val remainingSeconds: Long) : MasterCredentialVerifyResult()
  data class InvalidCredential(val attemptsRemaining: Int) : MasterCredentialVerifyResult()
  object NotConfigured : MasterCredentialVerifyResult()
}

/**
 * Manages Master PIN/Passphrase credentials and integrity state for SecureVault.
 *
 * Uses PBKDF2WithHmacSHA256 (210,000 iterations) for credential derivation,
 * constant-time equality checks, rate limiting with exponential backoff lockout,
 * and hardware-backed AES-256-GCM encryption via [KeystoreCipherProvider] under the
 * isolated alias "securevault_credential_key".
 */
object SecureVaultBiometricTracker {
  private const val TAG = "SecureVaultBiometricTracker"

  // Distinct hardware Keystore alias dedicated exclusively to credential integrity
  const val KEYSTORE_KEY_ALIAS = "securevault_credential_key"

  // Modern SharedPreferences file name
  const val PREFS_FILE = "securevault_system_integrity_prefs"

  // Plain metadata keys
  private const val KEY_LAST_APP_VERSION = "last_app_version_code"
  private const val KEY_LAST_OS_SDK = "last_os_sdk_int"
  private const val KEY_LAST_OS_INCREMENTAL = "last_os_incremental"
  private const val KEY_CREDENTIAL_TYPE = "master_credential_type"
  private const val KEY_EXPLICITLY_CONFIGURED = "master_credential_explicitly_configured"

  // Sensitive credential & rate-limiting fields encrypted with KeystoreCipherProvider
  private const val KEY_ENCRYPTED_PASSPHRASE_HASH = "enc_master_passphrase_hash"
  private const val KEY_ENCRYPTED_PASSPHRASE_SALT = "enc_master_passphrase_salt"
  private const val KEY_ENCRYPTED_FAILED_ATTEMPTS = "enc_failed_master_credential_attempts"
  private const val KEY_ENCRYPTED_LOCKOUT_UNTIL_EPOCH_MS = "enc_master_credential_lockout_until_ms"

  private const val MAX_FREE_ATTEMPTS = 5
  private const val BASE_LOCKOUT_SECONDS = 30L

  @Volatile
  private var cachedPrefs: SharedPreferences? = null

  @VisibleForTesting
  internal fun resetForTesting() {
    cachedPrefs = null
  }

  private fun getPrefs(context: Context): SharedPreferences {
    val appContext = context.applicationContext
    cachedPrefs?.let { return it }

    synchronized(this) {
      cachedPrefs?.let { return it }
      val plainPrefs = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
      cachedPrefs = plainPrefs
      return plainPrefs
    }
  }

  private fun putEncryptedString(context: Context, plainPrefs: SharedPreferences, key: String, value: String) {
    try {
      val encrypted = KeystoreCipherProvider.encryptString(KEYSTORE_KEY_ALIAS, value)
      plainPrefs.edit().putString(key, encrypted).apply()
    } catch (e: Exception) {
      Log.e(TAG, "KeystoreCipherProvider encryption failed for key '$key': ${e.message}", e)
      throw KeystoreUnavailableException(
        "Failed to encrypt credential state: hardware security module unavailable or malfunctioning.",
        e
      )
    }
  }

  private fun getEncryptedString(context: Context, plainPrefs: SharedPreferences, key: String): String? {
    val encrypted = plainPrefs.getString(key, null) ?: return null
    try {
      return KeystoreCipherProvider.decryptString(KEYSTORE_KEY_ALIAS, encrypted)
    } catch (e: Exception) {
      Log.e(TAG, "KeystoreCipherProvider decryption failed for key '$key': ${e.message}", e)
      throw KeystoreUnavailableException(
        "Failed to decrypt credential state: hardware security module unavailable or malfunctioning.",
        e
      )
    }
  }

  private fun removeEncryptedKeys(context: Context, plainPrefs: SharedPreferences, vararg keys: String) {
    val plainEditor = plainPrefs.edit()
    for (key in keys) {
      plainEditor.remove(key)
    }
    plainEditor.apply()
  }

  fun isExplicitlyConfigured(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_EXPLICITLY_CONFIGURED, false)
  }

  fun getRemainingLockoutSeconds(context: Context): Long {
    val prefs = getPrefs(context)
    val lockoutStr = getEncryptedString(context, prefs, KEY_ENCRYPTED_LOCKOUT_UNTIL_EPOCH_MS)
    val lockoutUntil = lockoutStr?.toLongOrNull() ?: 0L
    val now = System.currentTimeMillis()
    return if (now < lockoutUntil) {
      ((lockoutUntil - now + 999L) / 1000L).coerceAtLeast(1L)
    } else {
      0L
    }
  }

  fun getFailedAttemptsCount(context: Context): Int {
    val prefs = getPrefs(context)
    val attemptsStr = getEncryptedString(context, prefs, KEY_ENCRYPTED_FAILED_ATTEMPTS)
    return attemptsStr?.toIntOrNull() ?: 0
  }

  fun checkUpdateContext(context: Context): VaultUpdateType {
    val prefs = getPrefs(context)
    val lastAppVersion = prefs.getInt(KEY_LAST_APP_VERSION, -1)
    val lastOsSdk = prefs.getInt(KEY_LAST_OS_SDK, -1)
    val lastOsIncremental = prefs.getString(KEY_LAST_OS_INCREMENTAL, null)

    val currentAppVersion = BuildConfig.VERSION_CODE
    val currentOsSdk = Build.VERSION.SDK_INT
    val currentOsIncremental = Build.VERSION.INCREMENTAL

    if (lastAppVersion == -1) {
      // First run - initialize baseline
      recordCurrentMetrics(context)
      return VaultUpdateType.NONE
    }

    if (currentOsSdk != lastOsSdk || (currentOsIncremental != null && currentOsIncremental != lastOsIncremental)) {
      return VaultUpdateType.OS_UPDATE
    }

    if (currentAppVersion != lastAppVersion) {
      return VaultUpdateType.APP_UPDATE
    }

    return VaultUpdateType.NONE
  }

  fun recordCurrentMetrics(context: Context) {
    val prefs = getPrefs(context)
    prefs.edit()
      .putInt(KEY_LAST_APP_VERSION, BuildConfig.VERSION_CODE)
      .putInt(KEY_LAST_OS_SDK, Build.VERSION.SDK_INT)
      .putString(KEY_LAST_OS_INCREMENTAL, Build.VERSION.INCREMENTAL)
      .apply()
  }

  fun getMasterCredentialType(context: Context): MasterCredentialType {
    val prefs = getPrefs(context)
    val typeName = prefs.getString(KEY_CREDENTIAL_TYPE, null) ?: return MasterCredentialType.PIN
    return try {
      MasterCredentialType.valueOf(typeName)
    } catch (e: Exception) {
      MasterCredentialType.PIN
    }
  }

  fun isMasterPassphraseSet(context: Context): Boolean {
    val prefs = getPrefs(context)
    val hash = getEncryptedString(context, prefs, KEY_ENCRYPTED_PASSPHRASE_HASH)
    val salt = getEncryptedString(context, prefs, KEY_ENCRYPTED_PASSPHRASE_SALT)
    return hash != null && salt != null
  }

  fun setMasterCredential(context: Context, secret: String, type: MasterCredentialType) {
    require(secret.length >= type.minLength) {
      "${type.title} must be at least ${type.minLength} characters."
    }
    if (type == MasterCredentialType.PIN) {
      require(secret.all { it.isDigit() }) {
        "Master PIN must contain digits only."
      }
    }

    val salt = ByteArray(16)
    SecureRandom().nextBytes(salt)
    val hash = hashPassphrase(secret, salt)

    val prefs = getPrefs(context)
    val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
    val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)

    putEncryptedString(context, prefs, KEY_ENCRYPTED_PASSPHRASE_SALT, saltB64)
    putEncryptedString(context, prefs, KEY_ENCRYPTED_PASSPHRASE_HASH, hashB64)

    prefs.edit()
      .putString(KEY_CREDENTIAL_TYPE, type.name)
      .putBoolean(KEY_EXPLICITLY_CONFIGURED, true)
      .apply()

    removeEncryptedKeys(context, prefs, KEY_ENCRYPTED_FAILED_ATTEMPTS, KEY_ENCRYPTED_LOCKOUT_UNTIL_EPOCH_MS)

    CryptoLogger.hardware("CREDENTIAL_CONFIGURED", "${type.title} initialized with PBKDF2 derivation (210,000 iterations).")
  }

  fun setMasterPassphrase(context: Context, passphrase: String) {
    val isAllDigits = passphrase.all { it.isDigit() }
    val type = if (isAllDigits && passphrase.length <= 8) MasterCredentialType.PIN else MasterCredentialType.PASSPHRASE
    setMasterCredential(context, passphrase, type)
  }

  fun changeMasterCredential(
    context: Context,
    oldSecret: String?,
    newSecret: String,
    newType: MasterCredentialType
  ): Pair<Boolean, String> {
    if (isExplicitlyConfigured(context)) {
      if (oldSecret == null) {
        return Pair(false, "Current Master Credential is required.")
      }
      when (val verifyResult = verifyMasterCredential(context, oldSecret)) {
        is MasterCredentialVerifyResult.Success -> { /* authorized */ }
        is MasterCredentialVerifyResult.LockedOut -> {
          return Pair(false, "Too many failed attempts. Try again in ${verifyResult.remainingSeconds} seconds.")
        }
        is MasterCredentialVerifyResult.InvalidCredential -> {
          val remainingMsg = if (verifyResult.attemptsRemaining > 0) {
            " (${verifyResult.attemptsRemaining} attempts left before lockout)"
          } else {
            ""
          }
          return Pair(false, "Current Master Credential is incorrect$remainingMsg.")
        }
        is MasterCredentialVerifyResult.NotConfigured -> {
          return Pair(false, "No Master Credential configured.")
        }
      }
    }
    if (newSecret.length < newType.minLength) {
      return Pair(false, "${newType.title} must be at least ${newType.minLength} characters.")
    }
    if (newType == MasterCredentialType.PIN && !newSecret.all { it.isDigit() }) {
      return Pair(false, "Master PIN must contain digits only.")
    }

    setMasterCredential(context, newSecret, newType)
    return Pair(true, "${newType.title} successfully updated.")
  }

  fun verifyMasterCredential(context: Context, input: String): MasterCredentialVerifyResult {
    val prefs = getPrefs(context)

    // Check rate limit / lockout
    val remainingLockout = getRemainingLockoutSeconds(context)
    if (remainingLockout > 0) {
      CryptoLogger.warn("CREDENTIAL_RATE_LIMITED", "Verification blocked. Lockout active for ${remainingLockout}s.")
      return MasterCredentialVerifyResult.LockedOut(remainingLockout)
    }

    val saltB64 = getEncryptedString(context, prefs, KEY_ENCRYPTED_PASSPHRASE_SALT)
    val hashB64 = getEncryptedString(context, prefs, KEY_ENCRYPTED_PASSPHRASE_HASH)
    val isConfigured = prefs.getBoolean(KEY_EXPLICITLY_CONFIGURED, false)

    // Backdoor removed: Never auto-accept or silently provision!
    if (!isConfigured || saltB64 == null || hashB64 == null) {
      CryptoLogger.warn("CREDENTIAL_UNCONFIGURED", "Attempted verification but no master credential configured.")
      return MasterCredentialVerifyResult.NotConfigured
    }

    val salt = try {
      Base64.decode(saltB64, Base64.NO_WRAP)
    } catch (e: Exception) {
      return MasterCredentialVerifyResult.NotConfigured
    }

    val expectedHash = try {
      Base64.decode(hashB64, Base64.NO_WRAP)
    } catch (e: Exception) {
      return MasterCredentialVerifyResult.NotConfigured
    }

    val computedHash = hashPassphrase(input, salt)
    val isMatch = MessageDigest.isEqual(expectedHash, computedHash)

    if (isMatch) {
      // Successful verification: reset failed attempts and lockout
      removeEncryptedKeys(context, prefs, KEY_ENCRYPTED_FAILED_ATTEMPTS, KEY_ENCRYPTED_LOCKOUT_UNTIL_EPOCH_MS)
      CryptoLogger.hardware("CREDENTIAL_VERIFIED", "Master credential successfully verified via PBKDF2.")
      return MasterCredentialVerifyResult.Success
    } else {
      // Failed verification: increment failure count & apply escalating delay
      val failedCount = getFailedAttemptsCount(context) + 1
      putEncryptedString(context, prefs, KEY_ENCRYPTED_FAILED_ATTEMPTS, failedCount.toString())
      val attemptsRemaining = (MAX_FREE_ATTEMPTS - failedCount).coerceAtLeast(0)

      if (failedCount >= MAX_FREE_ATTEMPTS) {
        // 5th failure: 30s, 6th: 60s, 7th: 120s, 8th: 240s, max 3600s (1 hour)
        val shift = (failedCount - MAX_FREE_ATTEMPTS).coerceAtMost(6)
        val lockoutSeconds = (BASE_LOCKOUT_SECONDS * (1L shl shift)).coerceAtMost(3600L)
        val lockoutUntil = System.currentTimeMillis() + (lockoutSeconds * 1000L)
        putEncryptedString(context, prefs, KEY_ENCRYPTED_LOCKOUT_UNTIL_EPOCH_MS, lockoutUntil.toString())
        CryptoLogger.warn("CREDENTIAL_LOCKOUT_TRIGGERED", "Failed attempt $failedCount. Locked out for ${lockoutSeconds}s.")
        return MasterCredentialVerifyResult.LockedOut(lockoutSeconds)
      } else {
        CryptoLogger.warn("CREDENTIAL_FAILED", "Failed attempt $failedCount. $attemptsRemaining attempts before lockout.")
        return MasterCredentialVerifyResult.InvalidCredential(attemptsRemaining)
      }
    }
  }

  fun verifyMasterPassphrase(context: Context, input: String): Boolean {
    return verifyMasterCredential(context, input) is MasterCredentialVerifyResult.Success
  }

  private fun hashPassphrase(passphrase: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, 210_000, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return factory.generateSecret(spec).encoded
  }

  fun evaluateBiometricKeyInvalidation(context: Context): BiometricRosterState {
    val updateType = checkUpdateContext(context)
    val hasConfigured = isExplicitlyConfigured(context)
    val credentialType = getMasterCredentialType(context)
    val credName = credentialType.title

    if (!hasConfigured) {
      CryptoLogger.warn("BIOMETRIC_KEY_INVALIDATED_UNRECOVERABLE", "Biometric key invalidated and no master credential was configured. Vault cannot be recovered.")
      return BiometricRosterState(
        isKeyInvalidated = true,
        updateType = updateType,
        alertMessage = "Biometric keys on this device were permanently invalidated. Because no recovery Master PIN or Passphrase was explicitly configured in advance, existing encrypted vault files cannot be recovered. You must reset the vault hardware key and start fresh.",
        isRogueAlert = updateType == VaultUpdateType.NONE,
        credentialType = credentialType,
        hasMasterCredential = false
      )
    }

    return when (updateType) {
      VaultUpdateType.APP_UPDATE -> {
        CryptoLogger.hardware("SYSTEM_UPDATE_RESYNC", "App update detected. Resyncing biometric credentials via $credName.")
        BiometricRosterState(
          isKeyInvalidated = true,
          updateType = updateType,
          alertMessage = "Welcome to the new version! Please enter your $credName once to re-sync security credentials.",
          isRogueAlert = false,
          credentialType = credentialType,
          hasMasterCredential = true
        )
      }
      VaultUpdateType.OS_UPDATE -> {
        CryptoLogger.hardware("SYSTEM_UPDATE_RESYNC", "OS update detected. Resyncing biometric credentials via $credName.")
        BiometricRosterState(
          isKeyInvalidated = true,
          updateType = updateType,
          alertMessage = "Device system update detected! Please enter your $credName once to re-sync hardware encryption credentials.",
          isRogueAlert = false,
          credentialType = credentialType,
          hasMasterCredential = true
        )
      }
      VaultUpdateType.NONE -> {
        CryptoLogger.warn("BIOMETRIC_ROSTER_CHANGED_ALERT", "Biometric enrollment changed on this device without system update! Hardware vault locked. $credName required.")
        BiometricRosterState(
          isKeyInvalidated = true,
          updateType = updateType,
          alertMessage = "Biometric settings on this device were changed. For your security, please enter your $credName to re-authenticate and verify your identity.",
          isRogueAlert = true,
          credentialType = credentialType,
          hasMasterCredential = true
        )
      }
    }
  }
}
