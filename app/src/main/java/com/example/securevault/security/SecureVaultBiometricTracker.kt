package com.example.securevault.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import com.example.BuildConfig
import com.example.securevault.logging.CryptoLogger
import java.security.MessageDigest
import java.security.SecureRandom

enum class VaultUpdateType {
  NONE,
  APP_UPDATE,
  OS_UPDATE
}

enum class MasterCredentialType(val title: String, val minLength: Int) {
  PIN("Master PIN", 4),
  PASSPHRASE("Master Passphrase", 6)
}

data class BiometricRosterState(
  val isKeyInvalidated: Boolean,
  val updateType: VaultUpdateType,
  val alertMessage: String,
  val isRogueAlert: Boolean,
  val credentialType: MasterCredentialType = MasterCredentialType.PIN
)

object SecureVaultBiometricTracker {
  private const val PREFS_NAME = "securevault_system_integrity"
  private const val KEY_LAST_APP_VERSION = "last_app_version_code"
  private const val KEY_LAST_OS_SDK = "last_os_sdk_int"
  private const val KEY_LAST_OS_INCREMENTAL = "last_os_incremental"
  private const val KEY_PASSPHRASE_HASH = "master_passphrase_hash"
  private const val KEY_PASSPHRASE_SALT = "master_passphrase_salt"
  private const val KEY_CREDENTIAL_TYPE = "master_credential_type"
  private const val KEY_EXPLICITLY_CONFIGURED = "master_credential_explicitly_configured"

  private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun isExplicitlyConfigured(context: Context): Boolean {
    return getPrefs(context).getBoolean(KEY_EXPLICITLY_CONFIGURED, false)
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
    return prefs.contains(KEY_PASSPHRASE_HASH) && prefs.contains(KEY_PASSPHRASE_SALT)
  }

  fun setMasterCredential(context: Context, secret: String, type: MasterCredentialType) {
    val salt = ByteArray(16)
    SecureRandom().nextBytes(salt)
    val hash = hashPassphrase(secret, salt)

    val prefs = getPrefs(context)
    prefs.edit()
      .putString(KEY_PASSPHRASE_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
      .putString(KEY_PASSPHRASE_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
      .putString(KEY_CREDENTIAL_TYPE, type.name)
      .putBoolean(KEY_EXPLICITLY_CONFIGURED, true)
      .apply()

    CryptoLogger.hardware("CREDENTIAL_CONFIGURED", "${type.title} initialized with salted SHA-256 derivation.")
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
    if (isExplicitlyConfigured(context) && oldSecret != null) {
      if (!verifyMasterPassphrase(context, oldSecret)) {
        return Pair(false, "Current Master Credential is incorrect.")
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

  fun verifyMasterPassphrase(context: Context, input: String): Boolean {
    val prefs = getPrefs(context)
    val saltB64 = prefs.getString(KEY_PASSPHRASE_SALT, null)
    val hashB64 = prefs.getString(KEY_PASSPHRASE_HASH, null)

    // If never set before, accept default root pin "123456" and auto-initialize as PIN
    if (saltB64 == null || hashB64 == null) {
      if (input == "123456" || input.length >= 4) {
        setMasterPassphrase(context, input)
        return true
      }
      return false
    }

    val salt = Base64.decode(saltB64, Base64.NO_WRAP)
    val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
    val computedHash = hashPassphrase(input, salt)

    return MessageDigest.isEqual(expectedHash, computedHash)
  }

  private fun hashPassphrase(passphrase: String, salt: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(salt)
    return md.digest(passphrase.toByteArray(Charsets.UTF_8))
  }

  fun evaluateBiometricKeyInvalidation(context: Context): BiometricRosterState {
    val updateType = checkUpdateContext(context)
    val credentialType = getMasterCredentialType(context)
    val credName = credentialType.title
    return when (updateType) {
      VaultUpdateType.APP_UPDATE -> {
        CryptoLogger.hardware("SYSTEM_UPDATE_RESYNC", "App update detected. Resyncing biometric credentials via $credName.")
        BiometricRosterState(
          isKeyInvalidated = true,
          updateType = updateType,
          alertMessage = "Welcome to the new version! Please enter your $credName once to re-sync security credentials.",
          isRogueAlert = false,
          credentialType = credentialType
        )
      }
      VaultUpdateType.OS_UPDATE -> {
        CryptoLogger.hardware("SYSTEM_UPDATE_RESYNC", "OS update detected. Resyncing biometric credentials via $credName.")
        BiometricRosterState(
          isKeyInvalidated = true,
          updateType = updateType,
          alertMessage = "Device system update detected! Please enter your $credName once to re-sync hardware encryption credentials.",
          isRogueAlert = false,
          credentialType = credentialType
        )
      }
      VaultUpdateType.NONE -> {
        CryptoLogger.warn("BIOMETRIC_ROSTER_CHANGED_ALERT", "Rogue biometric enrollment detected without system update! Hardware vault locked. $credName required.")
        BiometricRosterState(
          isKeyInvalidated = true,
          updateType = updateType,
          alertMessage = "Biometric settings on this device were changed. For your security, please enter your $credName to re-authenticate and verify your identity.",
          isRogueAlert = true,
          credentialType = credentialType
        )
      }
    }
  }
}
