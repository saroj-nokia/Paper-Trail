package com.example.securevault.ui

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.security.SecurityAuditPreferences
import com.example.data.security.SecurityIntegrityAuditor
import com.example.securevault.data.SecureVaultDatabase
import com.example.securevault.data.SecureVaultKeyManager
import com.example.securevault.data.SecureVaultRepository
import com.example.securevault.data.VaultStorageLocation
import com.example.securevault.model.SecureFileItem
import com.example.securevault.security.BiometricRosterState
import com.example.securevault.security.MasterCredentialType
import com.example.securevault.security.MasterCredentialVerifyResult
import com.example.securevault.security.SecureVaultAuthManager
import com.example.securevault.security.SecureVaultBiometricTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

data class TransferProgress(
  val isTransferring: Boolean = false,
  val fileName: String = "",
  val bytesTransferred: Long = 0L,
  val totalBytes: Long = 0L,
  val actionLabel: String = "" // "Encrypting & Importing", "Decrypting & Exporting", "Migrating Storage", "Creating Backup"
) {
  val progressFraction: Float
    get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class SecureVaultMediaPlayerState(
  val item: SecureFileItem,
  val cipher: Cipher
)

data class SecureVaultPreview(
  val item: SecureFileItem,
  val decryptedBytes: ByteArray?,
  val isTooLargeToPreview: Boolean = false
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as SecureVaultPreview
    if (item != other.item) return false
    if (isTooLargeToPreview != other.isTooLargeToPreview) return false
    return (decryptedBytes == null && other.decryptedBytes == null) ||
        (decryptedBytes != null && other.decryptedBytes != null && decryptedBytes.contentEquals(other.decryptedBytes))
  }

  override fun hashCode(): Int {
    var result = item.hashCode()
    result = 31 * result + (decryptedBytes?.contentHashCode() ?: 0)
    result = 31 * result + isTooLargeToPreview.hashCode()
    return result
  }
}

class SecureVaultViewModel(application: Application) : AndroidViewModel(application) {
  private val TAG = "SecureVaultViewModel"

  private val database by lazy {
    SecureVaultDatabase.getInstance(application)
  }

  val repository: SecureVaultRepository by lazy {
    SecureVaultRepository(database.secureVaultDao(), application)
  }

  val authManager: SecureVaultAuthManager by lazy {
    SecureVaultAuthManager(application)
  }

  val storageLocation: StateFlow<VaultStorageLocation> = repository.storagePreferences.currentLocation
  val customFolderUriString: StateFlow<String?> = repository.storagePreferences.customFolderUriString

  val secureFiles: StateFlow<List<SecureFileItem>> = repository.allFiles
    .flowOn(Dispatchers.Default)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _transferProgress = MutableStateFlow(TransferProgress())
  val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _infoMessage = MutableStateFlow<String?>(null)
  val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

  private val _activePreview = MutableStateFlow<SecureVaultPreview?>(null)
  val activePreview: StateFlow<SecureVaultPreview?> = _activePreview.asStateFlow()

  private val _activeMediaPlayback = MutableStateFlow<SecureVaultMediaPlayerState?>(null)
  val activeMediaPlayback: StateFlow<SecureVaultMediaPlayerState?> = _activeMediaPlayback.asStateFlow()

  private val _biometricRosterAlert = MutableStateFlow<BiometricRosterState?>(null)
  val biometricRosterAlert: StateFlow<BiometricRosterState?> = _biometricRosterAlert.asStateFlow()

  private val _showPassphrasePrompt = MutableStateFlow<Boolean>(false)
  val showPassphrasePrompt: StateFlow<Boolean> = _showPassphrasePrompt.asStateFlow()

  private val _showCredentialSetupDialog = MutableStateFlow<Boolean>(false)
  val showCredentialSetupDialog: StateFlow<Boolean> = _showCredentialSetupDialog.asStateFlow()

  private val _masterCredentialType = MutableStateFlow<MasterCredentialType>(
    SecureVaultBiometricTracker.getMasterCredentialType(getApplication())
  )
  val masterCredentialType: StateFlow<MasterCredentialType> = _masterCredentialType.asStateFlow()

  init {
    viewModelScope.launch {
      repository.verifyIntegrityAndSelfHeal()
      _masterCredentialType.value = SecureVaultBiometricTracker.getMasterCredentialType(getApplication())
    }
  }

  fun refreshMasterCredentialType() {
    _masterCredentialType.value = SecureVaultBiometricTracker.getMasterCredentialType(getApplication())
  }

  fun isMasterCredentialConfigured(): Boolean {
    return SecureVaultBiometricTracker.isExplicitlyConfigured(getApplication())
  }

  fun openCredentialSetupDialog() {
    _showCredentialSetupDialog.value = true
  }

  fun dismissCredentialSetupDialog() {
    _showCredentialSetupDialog.value = false
  }

  fun saveMasterCredential(
    currentSecret: String?,
    newSecret: String,
    type: MasterCredentialType
  ): Boolean {
    val context = getApplication<Application>()
    val isExplicit = SecureVaultBiometricTracker.isExplicitlyConfigured(context)

    if (isExplicit) {
      if (currentSecret.isNullOrBlank()) {
        _errorMessage.value = "Current ${type.title} is required to change credentials."
        return false
      }
      val (success, message) = SecureVaultBiometricTracker.changeMasterCredential(context, currentSecret, newSecret, type)
      if (success) {
        _masterCredentialType.value = type
        _showCredentialSetupDialog.value = false
        _infoMessage.value = message
        return true
      } else {
        _errorMessage.value = message
        return false
      }
    } else {
      if (newSecret.length < type.minLength) {
        _errorMessage.value = "${type.title} must be at least ${type.minLength} characters."
        return false
      }
      if (type == MasterCredentialType.PIN && !newSecret.all { it.isDigit() }) {
        _errorMessage.value = "Master PIN must contain digits only."
        return false
      }
      SecureVaultBiometricTracker.setMasterCredential(context, newSecret, type)
      _masterCredentialType.value = type
      _showCredentialSetupDialog.value = false
      _infoMessage.value = "${type.title} successfully configured."
      return true
    }
  }

  fun clearError() {
    _errorMessage.value = null
  }

  fun clearInfo() {
    _infoMessage.value = null
  }

  fun openPassphrasePrompt() {
    refreshMasterCredentialType()
    _showPassphrasePrompt.value = true
  }

  fun dismissBiometricAlert() {
    _biometricRosterAlert.value = null
    _showPassphrasePrompt.value = false
  }

  fun resetVaultAfterUnrecoverableInvalidation(activity: FragmentActivity) {
    SecureVaultKeyManager.resetAndRegenerateKey()
    SecureVaultBiometricTracker.recordCurrentMetrics(activity)
    _biometricRosterAlert.value = null
    _showPassphrasePrompt.value = false
    authManager.lock()
    viewModelScope.launch {
      com.example.securevault.data.SecureVaultMetadataSafety.clearShadowSnapshot(activity)
    }
    _errorMessage.value = "Vault hardware key has been reset. All previous encrypted files remain permanently inaccessible. Please configure a new Master Credential."
  }

  fun verifyMasterPassphraseAndReEnroll(passphrase: String, activity: FragmentActivity): Boolean {
    val credType = SecureVaultBiometricTracker.getMasterCredentialType(activity)
    return when (val result = SecureVaultBiometricTracker.verifyMasterCredential(activity, passphrase)) {
      is MasterCredentialVerifyResult.Success -> {
        // Regenerate the Hardware KeyStore key for the new biometric roster
        SecureVaultKeyManager.resetAndRegenerateKey()
        SecureVaultBiometricTracker.recordCurrentMetrics(activity)
        _biometricRosterAlert.value = null
        _showPassphrasePrompt.value = false

        val newCipher = try {
          SecureVaultKeyManager.initEncryptCipher()
        } catch (e: Exception) {
          null
        }
        authManager.unlockDirectly(newCipher)
        _infoMessage.value = "Identity verified via ${credType.title}. Hardware vault unlocked."
        true
      }
      is MasterCredentialVerifyResult.LockedOut -> {
        _errorMessage.value = "Too many failed attempts. Verification locked out for ${result.remainingSeconds} seconds."
        false
      }
      is MasterCredentialVerifyResult.InvalidCredential -> {
        val remainingMsg = if (result.attemptsRemaining > 0) {
          " (${result.attemptsRemaining} attempts left before lockout)"
        } else {
          ""
        }
        _errorMessage.value = "Incorrect ${credType.title}. Please try again$remainingMsg."
        false
      }
      is MasterCredentialVerifyResult.NotConfigured -> {
        _errorMessage.value = "No Master Credential has been configured. Existing vault files cannot be recovered."
        false
      }
    }
  }

  fun setCustomFolderUri(uri: Uri?, activity: FragmentActivity) {
    if (uri != null) {
      try {
        activity.contentResolver.takePersistableUriPermission(
          uri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      } catch (e: Exception) {
        Log.w(TAG, "Could not take persistable URI permission: ${e.message}")
      }
    }
    repository.storagePreferences.setCustomFolderUri(uri)
    val name = repository.storagePreferences.getCustomFolderDisplayName(activity)
    if (name != null) {
      _infoMessage.value = "Custom vault folder set: $name"
    }
  }

  fun getCustomFolderDisplayName(activity: FragmentActivity): String? {
    return repository.storagePreferences.getCustomFolderDisplayName(activity)
  }

  fun lockVault() {
    authManager.lock()
    closePreview()
    closeMediaPlayer()
    _transferProgress.value = TransferProgress()
  }

  fun closePreview() {
    _activePreview.value = null
  }

  fun closeMediaPlayer() {
    _activeMediaPlayback.value = null
  }

  fun unlockVault(
    activity: FragmentActivity,
    onSuccess: () -> Unit = {}
  ) {
    _isLoading.value = true
    viewModelScope.launch {
      if (SecurityAuditPreferences.isStrictGateEnabled(activity)) {
        val report = withContext(Dispatchers.Default) {
          SecurityIntegrityAuditor.runFullAudit(activity)
        }
        if (report.hasCriticalFailures) {
          _isLoading.value = false
          _errorMessage.value = "Hardware security check failed: SELinux or storage encryption compromised."
          return@launch
        }
      }

      var cipher: Cipher? = null
      try {
        cipher = SecureVaultKeyManager.initEncryptCipher()
      } catch (e: Exception) {
        Log.w(TAG, "Cipher init encountered issue: ${e.message}")
        if (SecureVaultKeyManager.isKeyPermanentlyInvalidated(e)) {
          _isLoading.value = false
          val alertState = SecureVaultBiometricTracker.evaluateBiometricKeyInvalidation(activity)
          _biometricRosterAlert.value = alertState
          _showPassphrasePrompt.value = true
          return@launch
        }
      }

      try {
        authManager.promptBiometric(
          activity = activity,
          cipher = cipher,
          title = "Unlock SecureVault",
          subtitle = "Biometric confirmation required to open hardware-encrypted storage",
          onSuccess = {
            _isLoading.value = false
            onSuccess()
          },
          onError = { err ->
            _isLoading.value = false
            val lower = err.lowercase()
            if (lower.contains("invalidated") || lower.contains("key permanently") || lower.contains("roster")) {
              val alertState = SecureVaultBiometricTracker.evaluateBiometricKeyInvalidation(activity)
              _biometricRosterAlert.value = alertState
              _showPassphrasePrompt.value = true
            } else {
              _errorMessage.value = err
            }
          }
        )
      } catch (e: Exception) {
        _isLoading.value = false
        if (SecureVaultKeyManager.isKeyPermanentlyInvalidated(e)) {
          val alertState = SecureVaultBiometricTracker.evaluateBiometricKeyInvalidation(activity)
          _biometricRosterAlert.value = alertState
          _showPassphrasePrompt.value = true
        } else {
          _errorMessage.value = "Failed to start biometric authentication: ${e.message}"
        }
      }
    }
  }

  fun changeStorageLocation(
    targetLocation: VaultStorageLocation,
    migrateExisting: Boolean,
    activity: FragmentActivity,
    onSuccess: (Int) -> Unit = {}
  ) {
    if (!migrateExisting) {
      repository.storagePreferences.setStorageLocation(targetLocation)
      _infoMessage.value = "Storage location updated to: ${targetLocation.title}"
      onSuccess(0)
      return
    }

    _isLoading.value = true
    viewModelScope.launch {
      _transferProgress.value = TransferProgress(
        isTransferring = true,
        fileName = "Migrating Vault Files",
        actionLabel = "Moving Blobs to ${targetLocation.title}"
      )
      val result = repository.migrateStorage(
        targetLocation = targetLocation,
        onProgress = { count, total ->
          _transferProgress.value = _transferProgress.value.copy(
            bytesTransferred = count.toLong(),
            totalBytes = total.toLong()
          )
        }
      )
      _isLoading.value = false
      _transferProgress.value = TransferProgress()
      result.onSuccess { count ->
        _infoMessage.value = "Successfully migrated $count files to ${targetLocation.title}"
        onSuccess(count)
      }.onFailure { err ->
        _errorMessage.value = "Migration failed: ${err.message}"
      }
    }
  }

  fun exportVaultBackup(
    destinationUri: Uri,
    activity: FragmentActivity,
    onComplete: (Int) -> Unit = {}
  ) {
    _isLoading.value = true
    try {
      val cipher = SecureVaultKeyManager.initEncryptCipher()
      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Export Vault Backup",
        subtitle = "Confirm biometrics to generate an encrypted .vault backup archive",
        onSuccess = {
          viewModelScope.launch {
            _transferProgress.value = TransferProgress(
              isTransferring = true,
              fileName = "Encrypted Vault Archive (.vault)",
              actionLabel = "Exporting Backup Archive"
            )
            val result = repository.exportVaultBackup(
              destinationUri = destinationUri,
              onProgress = { count, total ->
                _transferProgress.value = _transferProgress.value.copy(
                  bytesTransferred = count.toLong(),
                  totalBytes = total.toLong()
                )
              }
            )
            _isLoading.value = false
            _transferProgress.value = TransferProgress()
            result.onSuccess { count ->
              _infoMessage.value = "Vault backup exported successfully ($count files included)"
              onComplete(count)
            }.onFailure { err ->
              _errorMessage.value = "Failed to export vault backup: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to export backup: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Backup export error: ${e.message}"
    }
  }

  fun restoreVaultBackup(
    sourceUri: Uri,
    activity: FragmentActivity,
    onComplete: (Int) -> Unit = {}
  ) {
    _isLoading.value = true
    try {
      val cipher = SecureVaultKeyManager.initEncryptCipher()
      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Restore Vault Backup",
        subtitle = "Confirm biometrics to restore encrypted archive into SecureVault",
        onSuccess = {
          viewModelScope.launch {
            _transferProgress.value = TransferProgress(
              isTransferring = true,
              fileName = "Restoring Vault Archive",
              actionLabel = "Importing Files from Backup"
            )
            val result = repository.restoreVaultBackup(
              sourceUri = sourceUri,
              onProgress = { count, total ->
                _transferProgress.value = _transferProgress.value.copy(
                  bytesTransferred = count.toLong(),
                  totalBytes = total.toLong()
                )
              }
            )
            _isLoading.value = false
            _transferProgress.value = TransferProgress()
            result.onSuccess { count ->
              _infoMessage.value = "Restored $count files successfully from backup archive"
              onComplete(count)
            }.onFailure { err ->
              _errorMessage.value = "Failed to restore vault backup: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to restore backup: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Backup restore error: ${e.message}"
    }
  }

  fun importFile(
    uri: Uri,
    activity: FragmentActivity,
    onComplete: () -> Unit = {}
  ) {
    _isLoading.value = true
    try {
      val cipher = SecureVaultKeyManager.initEncryptCipher()
      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Encrypt & Store File",
        subtitle = "Confirm biometrics to authorize hardware AES-256 encryption",
        onSuccess = { authenticatedCipher ->
          val validCipher = authenticatedCipher ?: cipher
          viewModelScope.launch {
            _transferProgress.value = TransferProgress(
              isTransferring = true,
              fileName = "Selected File",
              actionLabel = "Encrypting & Storing"
            )
            val result = repository.importFile(
              uri = uri,
              cipher = validCipher,
              onProgress = { written, total ->
                _transferProgress.value = _transferProgress.value.copy(
                  bytesTransferred = written,
                  totalBytes = total
                )
              }
            )
            _isLoading.value = false
            _transferProgress.value = TransferProgress()
            result.onSuccess {
              onComplete()
            }.onFailure { err ->
              _errorMessage.value = "Failed to encrypt file: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to encrypt: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Encryption initialization error: ${e.message}"
    }
  }

  fun exportFile(
    item: SecureFileItem,
    destinationUri: Uri,
    activity: FragmentActivity,
    onComplete: () -> Unit = {}
  ) {
    _isLoading.value = true
    try {
      val ivStringToUse = if (item.wrappedDek.isNotEmpty() && item.dekIv.isNotEmpty()) {
        item.dekIv
      } else {
        item.iv
      }
      val ivBytes = Base64.decode(ivStringToUse, Base64.NO_WRAP)
      val cipher = SecureVaultKeyManager.initDecryptCipher(ivBytes)

      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Export & Decrypt File",
        subtitle = "Confirm biometrics to stream-decrypt to external storage",
        onSuccess = { authenticatedCipher ->
          val validCipher = authenticatedCipher ?: cipher
          viewModelScope.launch {
            _transferProgress.value = TransferProgress(
              isTransferring = true,
              fileName = item.originalFileName,
              actionLabel = "Decrypting & Exporting"
            )
            val result = repository.exportFile(
              item = item,
              destinationUri = destinationUri,
              cipher = validCipher,
              onProgress = { exported, total ->
                _transferProgress.value = _transferProgress.value.copy(
                  bytesTransferred = exported,
                  totalBytes = total
                )
              }
            )
            _isLoading.value = false
            _transferProgress.value = TransferProgress()
            result.onSuccess {
              onComplete()
            }.onFailure { err ->
              _errorMessage.value = "Failed to export file: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to export: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Export initialization error: ${e.message}"
    }
  }

  fun previewFile(
    item: SecureFileItem,
    activity: FragmentActivity
  ) {
    _isLoading.value = true
    try {
      val ivStringToUse = if (item.wrappedDek.isNotEmpty() && item.dekIv.isNotEmpty()) {
        item.dekIv
      } else {
        item.iv
      }
      val ivBytes = Base64.decode(ivStringToUse, Base64.NO_WRAP)
      val cipher = SecureVaultKeyManager.initDecryptCipher(ivBytes)

      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Decrypt for Preview",
        subtitle = "Confirm biometrics to authorize in-memory hardware decryption",
        onSuccess = { authenticatedCipher ->
          val validCipher = authenticatedCipher ?: cipher
          viewModelScope.launch {
            val result = repository.decryptFile(item, validCipher)
            _isLoading.value = false
            result.onSuccess { decResult ->
              when (decResult) {
                is com.example.securevault.data.DecryptionResult.Success -> {
                  _activePreview.value = SecureVaultPreview(item, decResult.bytes, isTooLargeToPreview = false)
                }
                is com.example.securevault.data.DecryptionResult.TooLargeToPreview -> {
                  _activePreview.value = SecureVaultPreview(item, null, isTooLargeToPreview = true)
                }
              }
            }.onFailure { err ->
              _errorMessage.value = "Decryption failed: ${err.message}"
            }
          }
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to decrypt: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Decryption initialization error: ${e.message}"
    }
  }

  fun playMediaFile(
    item: SecureFileItem,
    activity: FragmentActivity
  ) {
    _isLoading.value = true
    try {
      val ivStringToUse = if (item.wrappedDek.isNotEmpty() && item.dekIv.isNotEmpty()) {
        item.dekIv
      } else {
        item.iv
      }
      val ivBytes = Base64.decode(ivStringToUse, Base64.NO_WRAP)
      val cipher = SecureVaultKeyManager.initDecryptCipher(ivBytes)

      authManager.promptBiometric(
        activity = activity,
        cipher = cipher,
        title = "Play Secure Media",
        subtitle = "Confirm biometrics to authorize real-time stream decryption",
        onSuccess = { authenticatedCipher ->
          _isLoading.value = false
          val validCipher = authenticatedCipher ?: cipher
          _activeMediaPlayback.value = SecureVaultMediaPlayerState(item, validCipher)
        },
        onError = { err ->
          _isLoading.value = false
          _errorMessage.value = "Biometric authorization required to play media: $err"
        }
      )
    } catch (e: Exception) {
      _isLoading.value = false
      _errorMessage.value = "Playback initialization error: ${e.message}"
    }
  }

  fun deleteFile(item: SecureFileItem) {
    viewModelScope.launch {
      if (_activePreview.value?.item?.id == item.id) {
        closePreview()
      }
      if (_activeMediaPlayback.value?.item?.id == item.id) {
        closeMediaPlayer()
      }
      repository.deleteFile(item)
    }
  }
}
