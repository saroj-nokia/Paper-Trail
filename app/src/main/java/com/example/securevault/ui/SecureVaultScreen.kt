package com.example.securevault.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.security.IntegrityReport
import com.example.data.security.SecurityAuditPreferences
import com.example.data.security.SecurityIntegrityAuditor
import com.example.securevault.data.VaultStorageLocation
import com.example.securevault.model.SecureFileItem
import com.example.securevault.security.BiometricRosterState
import com.example.securevault.security.MasterCredentialType
import com.example.securevault.security.VaultUpdateType
import com.example.ui.screens.auth.SecurityIntegrityGateScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SecureVaultAmber = Color(0xFFD97706)
private val SecureVaultAmberContainer = Color(0xFFFEF3C7)
private val SecureVaultOnAmberContainer = Color(0xFF92400E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(
  onNavigateBack: () -> Unit = {},
  viewModel: SecureVaultViewModel = viewModel()
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val lifecycleOwner = LocalLifecycleOwner.current

  val isUnlocked by viewModel.authManager.isUnlocked.collectAsStateWithLifecycle()
  val secureFiles by viewModel.secureFiles.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
  val transferProgress by viewModel.transferProgress.collectAsStateWithLifecycle()
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()
  val activePreview by viewModel.activePreview.collectAsStateWithLifecycle()
  val activeMediaPlayback by viewModel.activeMediaPlayback.collectAsStateWithLifecycle()
  val storageLocation by viewModel.storageLocation.collectAsStateWithLifecycle()
  val customFolderUri by viewModel.customFolderUriString.collectAsStateWithLifecycle()
  val biometricRosterAlert by viewModel.biometricRosterAlert.collectAsStateWithLifecycle()
  val showPassphrasePrompt by viewModel.showPassphrasePrompt.collectAsStateWithLifecycle()
  val showCredentialSetupDialog by viewModel.showCredentialSetupDialog.collectAsStateWithLifecycle()
  val masterCredentialType by viewModel.masterCredentialType.collectAsStateWithLifecycle()

  // Hardware Security Strict Gate check
  val isStrictGateEnabled = remember { SecurityAuditPreferences.isStrictGateEnabled(context) }
  var integrityReport by remember { mutableStateOf<IntegrityReport?>(null) }
  var isAuditing by remember { mutableStateOf(false) }

  LaunchedEffect(isStrictGateEnabled) {
    if (isStrictGateEnabled) {
      isAuditing = true
      val report = withContext(Dispatchers.Default) {
        SecurityIntegrityAuditor.runFullAudit(context)
      }
      integrityReport = report
      isAuditing = false
    } else {
      integrityReport = null
      isAuditing = false
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()
  var itemToDelete by remember { mutableStateOf<SecureFileItem?>(null) }
  var itemToExport by remember { mutableStateOf<SecureFileItem?>(null) }
  var showStorageDialog by remember { mutableStateOf(false) }
  var showCryptoTerminal by remember { mutableStateOf(false) }

  // Re-lock whenever the screen leaves composition or app is backgrounded
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        viewModel.lockVault()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      viewModel.lockVault()
    }
  }

  // SAF Document Picker for importing any file type (including large movies and APKs)
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null && activity != null) {
      viewModel.importFile(uri, activity)
    }
  }

  // SAF Document Creator for streaming decrypted files to external storage
  val fileExportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("*/*")
  ) { uri ->
    val targetItem = itemToExport
    if (uri != null && targetItem != null && activity != null) {
      viewModel.exportFile(targetItem, uri, activity) {
        coroutineScope.launch {
          snackbarHostState.showSnackbar("Successfully exported ${targetItem.originalFileName}")
        }
      }
    }
    itemToExport = null
  }

  // SAF Document Creator for exporting full encrypted vault backup (.vault)
  val vaultBackupExportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
  ) { uri ->
    if (uri != null && activity != null) {
      viewModel.exportVaultBackup(uri, activity)
    }
  }

  // SAF Document Picker for restoring full encrypted vault backup (.vault)
  val vaultBackupRestoreLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null && activity != null) {
      viewModel.restoreVaultBackup(uri, activity)
    }
  }

  // SAF Document Tree Picker for Persistent Custom Folder location
  val openDocumentTreeLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    if (uri != null && activity != null) {
      viewModel.setCustomFolderUri(uri, activity)
    }
  }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearError()
    }
  }

  LaunchedEffect(infoMessage) {
    infoMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearInfo()
    }
  }

  val activeIntegrityReport = integrityReport
  if (isStrictGateEnabled && activeIntegrityReport != null && activeIntegrityReport.hasCriticalFailures) {
    // Isolated hardware security failure: Gate SecureVault without impacting the rest of Paper Trail
    SecurityIntegrityGateScreen(
      initialReport = activeIntegrityReport,
      onResolved = { newReport ->
        integrityReport = newReport
      },
      onReturnToApp = onNavigateBack
    )
  } else if (!isUnlocked) {
    // Dedicated Biometric Lock Screen for SecureVault
    SecureVaultLockGate(
      masterCredentialType = masterCredentialType,
      onUnlock = {
        activity?.let {
          viewModel.unlockVault(it)
        }
      },
      onPassphraseUnlock = {
        viewModel.openPassphrasePrompt()
      },
      isLoading = isLoading || isAuditing
    )
  } else {
    // Unlocked SecureVault UI
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.EnhancedEncryption,
                contentDescription = null,
                tint = SecureVaultAmber,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "SecureVault",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Hardware AES-256 Storage",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          },
          actions = {
            IconButton(
              onClick = { showCryptoTerminal = true },
              modifier = Modifier.testTag("securevault_crypto_terminal_button")
            ) {
              Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Crypto Security Terminal Logs",
                tint = SecureVaultAmber
              )
            }

            IconButton(
              onClick = { showStorageDialog = true },
              modifier = Modifier.testTag("securevault_storage_options_button")
            ) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "Storage Location & Backup",
                tint = SecureVaultAmber
              )
            }

            IconButton(
              onClick = { viewModel.lockVault() },
              modifier = Modifier.testTag("securevault_lock_button")
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock SecureVault",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      },
      floatingActionButton = {
        ExtendedFloatingActionButton(
          onClick = {
            filePickerLauncher.launch(arrayOf("*/*"))
          },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text("Add File", fontWeight = FontWeight.Bold) },
          containerColor = SecureVaultAmber,
          contentColor = Color.White,
          modifier = Modifier.testTag("securevault_add_file_fab")
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        // Security Status & Storage Mode Banner
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SecureVaultAmberContainer)
            .border(1.dp, SecureVaultAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { showStorageDialog = true }
            .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SecureVaultOnAmberContainer,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "ENCRYPTED AT REST (AES-GCM)",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = SecureVaultOnAmberContainer,
                  letterSpacing = 0.5.sp
                )
                Text(
                  text = "Mode: ${storageLocation.title} • Tap to configure",
                  style = MaterialTheme.typography.bodySmall,
                  fontSize = 11.sp,
                  color = SecureVaultOnAmberContainer.copy(alpha = 0.85f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(SecureVaultAmber.copy(alpha = 0.2f))
                  .clickable { showCryptoTerminal = true }
                  .padding(horizontal = 6.dp, vertical = 3.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Open Crypto Terminal",
                    tint = SecureVaultOnAmberContainer,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "LOGS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = SecureVaultOnAmberContainer
                  )
                }
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "${secureFiles.size} ${if (secureFiles.size == 1) "file" else "files"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SecureVaultOnAmberContainer
              )
            }
          }
        }

        // Transfer Progress Banner (for large movies, APKs, migrations, and backups)
        if (transferProgress.isTransferring) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = SecureVaultAmber,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = transferProgress.actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                }
                if (transferProgress.totalBytes > 0) {
                  Text(
                    text = "${(transferProgress.progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecureVaultAmber
                  )
                }
              }
              Spacer(modifier = Modifier.height(8.dp))
              if (transferProgress.totalBytes > 0) {
                LinearProgressIndicator(
                  progress = { transferProgress.progressFraction },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = SecureVaultAmber,
                  trackColor = SecureVaultAmber.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = if (transferProgress.totalBytes > 1000) formatFileSize(transferProgress.bytesTransferred) else "${transferProgress.bytesTransferred} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = if (transferProgress.totalBytes > 1000) formatFileSize(transferProgress.totalBytes) else "${transferProgress.totalBytes} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              } else {
                LinearProgressIndicator(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = SecureVaultAmber,
                  trackColor = SecureVaultAmber.copy(alpha = 0.2f)
                )
                if (transferProgress.bytesTransferred > 0) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "${formatFileSize(transferProgress.bytesTransferred)} processed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        } else if (isLoading) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(32.dp),
              color = SecureVaultAmber
            )
          }
        }

        if (secureFiles.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Box(
                modifier = Modifier
                  .size(80.dp)
                  .clip(CircleShape)
                  .background(SecureVaultAmberContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.LockOpen,
                  contentDescription = null,
                  modifier = Modifier.size(40.dp),
                  tint = SecureVaultOnAmberContainer
                )
              }
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "SecureVault is Empty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Store sensitive documents, contracts, photos, and files. Files are saved as opaque ciphertext blobs and never cached unencrypted to disk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
              )
              Spacer(modifier = Modifier.height(20.dp))
              Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber),
                modifier = Modifier.testTag("securevault_empty_import_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import File", fontWeight = FontWeight.Bold)
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .testTag("securevault_files_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(secureFiles, key = { it.id }) { item ->
              SecureFileCard(
                item = item,
                onPreview = {
                  activity?.let { act ->
                    if (item.mimeType.startsWith("video/") || item.mimeType.startsWith("audio/")) {
                      viewModel.playMediaFile(item, act)
                    } else {
                      viewModel.previewFile(item, act)
                    }
                  }
                },
                onExport = {
                  itemToExport = item
                  fileExportLauncher.launch(item.originalFileName)
                },
                onDelete = { itemToDelete = item }
              )
            }
          }
        }
      }
    }
  }

  // Storage Location & Vault Backup Options Dialog
  if (showStorageDialog) {
    StorageSettingsDialog(
      currentLocation = storageLocation,
      customFolderUri = customFolderUri,
      customFolderDisplayName = if (activity != null) viewModel.getCustomFolderDisplayName(activity) else null,
      masterCredentialType = masterCredentialType,
      onConfigureMasterCredential = {
        showStorageDialog = false
        viewModel.openCredentialSetupDialog()
      },
      onPickCustomFolder = { openDocumentTreeLauncher.launch(null) },
      onSelectLocation = { targetLoc, migrate ->
        if (activity != null) {
          if (targetLoc == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER && customFolderUri.isNullOrBlank()) {
            openDocumentTreeLauncher.launch(null)
          } else {
            viewModel.changeStorageLocation(targetLoc, migrate, activity)
            showStorageDialog = false
          }
        }
      },
      onExportBackup = {
        showStorageDialog = false
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        vaultBackupExportLauncher.launch("SecureVault_Backup_$timestamp.vault")
      },
      onRestoreBackup = {
        showStorageDialog = false
        vaultBackupRestoreLauncher.launch(arrayOf("*/*"))
      },
      onDismiss = { showStorageDialog = false }
    )
  }

  // Delete Confirmation Dialog
  if (itemToDelete != null) {
    val file = itemToDelete!!
    AlertDialog(
      onDismissRequest = { itemToDelete = null },
      title = { Text("Delete Encrypted File?") },
      text = {
        Text("Are you sure you want to permanently delete \"${file.originalFileName}\"? The encrypted blob will be wiped from storage.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteFile(file)
            itemToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_secure_file")
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { itemToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // In-Memory Decryption Preview
  if (activePreview != null) {
    val preview = activePreview!!
    val isImage = preview.item.mimeType.startsWith("image/")
    val isPdf = preview.item.mimeType.equals("application/pdf", ignoreCase = true) ||
        preview.item.originalFileName.endsWith(".pdf", ignoreCase = true)

    if (isImage && !preview.isTooLargeToPreview && preview.decryptedBytes != null) {
      SecureImageViewerScreen(
        item = preview.item,
        decryptedBytes = preview.decryptedBytes,
        onBack = { viewModel.closePreview() }
      )
    } else if (isPdf && !preview.isTooLargeToPreview && preview.decryptedBytes != null) {
      SecurePdfViewerScreen(
        item = preview.item,
        decryptedBytes = preview.decryptedBytes,
        onBack = { viewModel.closePreview() }
      )
    } else {
      SecureFilePreviewDialog(
        preview = preview,
        onExport = {
          itemToExport = preview.item
          viewModel.closePreview()
          fileExportLauncher.launch(preview.item.originalFileName)
        },
        onDismiss = { viewModel.closePreview() }
      )
    }
  }

  // In-Memory Streaming Decrypted Media Player (ExoPlayer + Custom Media3 DataSource)
  if (activeMediaPlayback != null) {
    val mediaState = activeMediaPlayback!!
    SecureMediaPlayerScreen(
      item = mediaState.item,
      authorizedCipher = mediaState.cipher,
      repository = viewModel.repository,
      onBack = { viewModel.closeMediaPlayer() }
    )
  }

  // Real-Time Crypto Security Terminal Bottom Sheet
  if (showCryptoTerminal) {
    CryptoTerminalBottomSheet(
      onDismiss = { showCryptoTerminal = false }
    )
  }

  // Master Passphrase / PIN Re-Enrollment & Unlock Dialog
  if (showPassphrasePrompt) {
    MasterPassphraseDialog(
      alertState = biometricRosterAlert,
      defaultCredentialType = masterCredentialType,
      onVerify = { input ->
        activity?.let { act ->
          viewModel.verifyMasterPassphraseAndReEnroll(input, act)
        }
      },
      onDismiss = { viewModel.dismissBiometricAlert() }
    )
  }

  // Master Passphrase / PIN Setup & Configuration Dialog
  if (showCredentialSetupDialog) {
    MasterCredentialSetupDialog(
      currentType = masterCredentialType,
      isConfigured = viewModel.isMasterCredentialConfigured(),
      onSave = { current, newSecret, type ->
        viewModel.saveMasterCredential(current, newSecret, type)
      },
      onDismiss = { viewModel.dismissCredentialSetupDialog() }
    )
  }
}

@Composable
private fun StorageSettingsDialog(
  currentLocation: VaultStorageLocation,
  customFolderUri: String?,
  customFolderDisplayName: String?,
  masterCredentialType: MasterCredentialType,
  onConfigureMasterCredential: () -> Unit,
  onPickCustomFolder: () -> Unit,
  onSelectLocation: (target: VaultStorageLocation, migrate: Boolean) -> Unit,
  onExportBackup: () -> Unit,
  onRestoreBackup: () -> Unit,
  onDismiss: () -> Unit
) {
  var selectedLocation by remember { mutableStateOf(currentLocation) }
  var migrateExisting by remember { mutableStateOf(true) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .border(1.dp, SecureVaultAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Storage,
              contentDescription = null,
              tint = SecureVaultAmber,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Vault Storage & Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Choose how cipher blobs are stored",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Info notice about encryption
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = SecureVaultAmber,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Regardless of storage location, all files are strictly AES-256-GCM encrypted and cannot be decrypted without this app's hardware key.",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              lineHeight = 15.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Storage Mode Options with Pros & Cons
        Text(
          text = "Storage Locations",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        VaultStorageLocation.entries.forEach { loc ->
          StorageLocationOptionCard(
            location = loc,
            isSelected = selectedLocation == loc,
            isCurrent = currentLocation == loc,
            customFolderDisplayName = if (loc == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) customFolderDisplayName else null,
            onPickCustomFolder = onPickCustomFolder,
            onSelect = { selectedLocation = loc }
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Migrate option if location changed
        if (selectedLocation != currentLocation) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { migrateExisting = !migrateExisting }
              .padding(horizontal = 4.dp, vertical = 2.dp)
          ) {
            Checkbox(
              checked = migrateExisting,
              onCheckedChange = { migrateExisting = it },
              colors = CheckboxDefaults.colors(checkedColor = SecureVaultAmber)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Move existing encrypted files to new storage location",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val buttonText = when {
          selectedLocation == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER && customFolderUri.isNullOrBlank() ->
            "Select Folder (SAF) to Activate"
          selectedLocation != currentLocation ->
            "Apply & Switch Location"
          else ->
            "Keep Current Location"
        }

        Button(
          onClick = {
            if (selectedLocation == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER && customFolderUri.isNullOrBlank()) {
              onPickCustomFolder()
            } else {
              onSelectLocation(selectedLocation, migrateExisting && (selectedLocation != currentLocation))
            }
          },
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber)
        ) {
          Text(buttonText, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(14.dp))

        // Master Security Credential Section
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Master Security Credential",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Active fallback: ${masterCredentialType.title} • Key recovery token",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
          onClick = onConfigureMasterCredential,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("configure_master_credential_button")
        ) {
          Icon(
            imageVector = if (masterCredentialType == MasterCredentialType.PIN) Icons.Default.Pin else Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = SecureVaultAmber
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            "Configure / Change PIN or Passphrase",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = SecureVaultAmber
          )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(14.dp))

        // Backup & Disaster Recovery Section
        Text(
          text = "Encrypted Vault Archive (.vault)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Export your entire vault to a single encrypted archive file that survives Clear Data or uninstalls. Save to Google Drive, PC, or SD card.",
          style = MaterialTheme.typography.bodySmall,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onExportBackup,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecureVaultAmber)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export .vault", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecureVaultAmber)
          }

          OutlinedButton(
            onClick = onRestoreBackup,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Restore .vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun StorageLocationOptionCard(
  location: VaultStorageLocation,
  isSelected: Boolean,
  isCurrent: Boolean,
  customFolderDisplayName: String? = null,
  onPickCustomFolder: () -> Unit = {},
  onSelect: () -> Unit
) {
  val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) com.example.ui.theme.PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
    animationSpec = com.example.ui.theme.PaperTrailMotion.pressScaleSpec(),
    label = "press_scale"
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(12.dp))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) SecureVaultAmber else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current,
        onClick = onSelect
      ),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) SecureVaultAmberContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) SecureVaultAmber else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = location.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              if (isCurrent) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(SecureVaultAmber)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                  Text("ACTIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
              }
            }
            Text(
              text = location.badge,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = SecureVaultAmber
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = location.description,
        style = MaterialTheme.typography.bodySmall,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Custom Folder Picker Controls
      if (location == VaultStorageLocation.PERSISTENT_CUSTOM_FOLDER) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
          Column(modifier = Modifier.padding(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Icon(
                  imageVector = if (customFolderDisplayName != null) Icons.Default.FolderOpen else Icons.Default.WarningAmber,
                  contentDescription = null,
                  tint = if (customFolderDisplayName != null) SecureVaultAmber else MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                  Text(
                    text = if (customFolderDisplayName != null) "Configured Folder:" else "No folder selected yet",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (customFolderDisplayName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                  )
                  if (customFolderDisplayName != null) {
                    Text(
                      text = customFolderDisplayName,
                      style = MaterialTheme.typography.bodySmall,
                      fontSize = 11.sp,
                      color = SecureVaultAmber,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
              }

              OutlinedButton(
                onClick = onPickCustomFolder,
                modifier = Modifier.padding(start = 6.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = if (customFolderDisplayName != null) "Change" else "Choose Folder",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }

      // Pros and Cons breakdown
      Spacer(modifier = Modifier.height(8.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(8.dp)
      ) {
        // Pros
        Row(verticalAlignment = Alignment.Top) {
          Icon(
            imageVector = Icons.Default.ThumbUp,
            contentDescription = null,
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            location.pros.forEach { pro ->
              Text(
                text = "• $pro",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Cons
        Row(verticalAlignment = Alignment.Top) {
          Icon(
            imageVector = Icons.Default.ThumbDown,
            contentDescription = null,
            tint = Color(0xFFDC2626),
            modifier = Modifier.size(14.dp).padding(top = 2.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            location.cons.forEach { con ->
              Text(
                text = "• $con",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SecureVaultLockGate(
  masterCredentialType: MasterCredentialType,
  onUnlock: () -> Unit,
  onPassphraseUnlock: () -> Unit,
  isLoading: Boolean
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(108.dp)
          .clip(CircleShape)
          .background(SecureVaultAmberContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.EnhancedEncryption,
          contentDescription = null,
          modifier = Modifier.size(56.dp),
          tint = SecureVaultOnAmberContainer
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "SecureVault Protected",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "This isolated vault requires hardware biometric authentication to access and decrypt your private files.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp
      )

      Spacer(modifier = Modifier.height(32.dp))

      Button(
        onClick = onUnlock,
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("securevault_unlock_button"),
        colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber)
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
        } else {
          Icon(Icons.Default.Fingerprint, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Unlock SecureVault", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedButton(
        onClick = onPassphraseUnlock,
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("securevault_passphrase_button")
      ) {
        Icon(
          imageVector = if (masterCredentialType == MasterCredentialType.PIN) Icons.Default.Pin else Icons.Default.Key,
          contentDescription = null,
          tint = SecureVaultAmber,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (masterCredentialType == MasterCredentialType.PIN) "Use Master PIN" else "Use Master Passphrase",
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold
        )
      }
    }
  }
}

@Composable
private fun MasterPassphraseDialog(
  alertState: BiometricRosterState?,
  defaultCredentialType: MasterCredentialType = MasterCredentialType.PIN,
  onVerify: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var passphrase by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var activeMode by remember { mutableStateOf(defaultCredentialType) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = if (alertState?.isRogueAlert == true) Icons.Default.WarningAmber else if (activeMode == MasterCredentialType.PIN) Icons.Default.Pin else Icons.Default.Key,
        contentDescription = null,
        tint = if (alertState?.isRogueAlert == true) MaterialTheme.colorScheme.error else SecureVaultAmber,
        modifier = Modifier.size(36.dp)
      )
    },
    title = {
      Text(
        text = when {
          alertState?.isRogueAlert == true -> "Biometric Settings Changed"
          alertState?.updateType == VaultUpdateType.OS_UPDATE -> "System OS Update Detected"
          alertState?.updateType == VaultUpdateType.APP_UPDATE -> "App Update Detected"
          else -> if (activeMode == MasterCredentialType.PIN) "Master PIN" else "Master Passphrase"
        },
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (alertState != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(if (alertState.isRogueAlert) MaterialTheme.colorScheme.errorContainer else SecureVaultAmberContainer)
              .padding(12.dp)
          ) {
            Text(
              text = alertState.alertMessage,
              style = MaterialTheme.typography.bodySmall,
              color = if (alertState.isRogueAlert) MaterialTheme.colorScheme.onErrorContainer else SecureVaultOnAmberContainer,
              lineHeight = 18.sp
            )
          }
          Spacer(modifier = Modifier.height(14.dp))
        } else {
          Text(
            text = "Enter your ${activeMode.title} to verify identity and unlock your hardware-encrypted vault.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Tip: If you have not set a custom PIN yet, default initial PIN is 123456.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = SecureVaultAmber
          )
          Spacer(modifier = Modifier.height(14.dp))
        }

        // Mode switch tabs
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          MasterCredentialType.entries.forEach { type ->
            val isSelected = activeMode == type
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) SecureVaultAmber else Color.Transparent)
                .clickable {
                  activeMode = type
                  passphrase = ""
                }
                .padding(vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = type.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = passphrase,
          onValueChange = {
            if (activeMode == MasterCredentialType.PIN) {
              if (it.all { char -> char.isDigit() }) passphrase = it
            } else {
              passphrase = it
            }
          },
          label = { Text(activeMode.title) },
          placeholder = { Text(if (activeMode == MasterCredentialType.PIN) "Enter PIN digits" else "Enter Passphrase") },
          singleLine = true,
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = if (activeMode == MasterCredentialType.PIN) KeyboardType.NumberPassword else KeyboardType.Password),
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isPasswordVisible) "Hide secret" else "Show secret"
              )
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("master_passphrase_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (passphrase.isNotBlank()) {
            onVerify(passphrase)
          }
        },
        enabled = passphrase.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber),
        modifier = Modifier.testTag("confirm_master_passphrase_button")
      ) {
        Text(if (alertState != null) "Verify & Re-Sync" else "Unlock Vault")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun MasterCredentialSetupDialog(
  currentType: MasterCredentialType,
  isConfigured: Boolean,
  onSave: (current: String?, newSecret: String, type: MasterCredentialType) -> Boolean,
  onDismiss: () -> Unit
) {
  var selectedType by remember { mutableStateOf(currentType) }
  var currentSecret by remember { mutableStateOf("") }
  var newSecret by remember { mutableStateOf("") }
  var confirmSecret by remember { mutableStateOf("") }
  var isCurrentVisible by remember { mutableStateOf(false) }
  var isNewVisible by remember { mutableStateOf(false) }
  var isConfirmVisible by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }

  val isPin = selectedType == MasterCredentialType.PIN
  val minLength = selectedType.minLength

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = if (isPin) Icons.Default.Pin else Icons.Default.Key,
        contentDescription = null,
        tint = SecureVaultAmber,
        modifier = Modifier.size(36.dp)
      )
    },
    title = {
      Text(
        text = if (isConfigured) "Configure Master Credential" else "Set Master Credential",
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        if (!isConfigured) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(SecureVaultAmberContainer)
              .padding(10.dp)
          ) {
            Text(
              text = "No custom Master PIN or Passphrase has been configured yet. Set one now to ensure easy recovery if your biometric settings change.",
              style = MaterialTheme.typography.bodySmall,
              color = SecureVaultOnAmberContainer,
              lineHeight = 16.sp
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
          text = "Select whether you want to use a numeric PIN or a full alphanumeric Master Passphrase as your root fallback token.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Type Selector Tabs / Segmented Control
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          MasterCredentialType.entries.forEach { type ->
            val isSelected = selectedType == type
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) SecureVaultAmber else Color.Transparent)
                .clickable {
                  selectedType = type
                  localError = null
                }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (type == MasterCredentialType.PIN) Icons.Default.Pin else Icons.Default.Password,
                  contentDescription = null,
                  tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = type.title,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Error message box if any
        if (localError != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.errorContainer)
              .padding(8.dp)
          ) {
            Text(
              text = localError!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // If already set, require current secret
        if (isConfigured) {
          OutlinedTextField(
            value = currentSecret,
            onValueChange = {
              currentSecret = it
              localError = null
            },
            label = { Text("Current ${currentType.title}") },
            singleLine = true,
            visualTransformation = if (isCurrentVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (currentType == MasterCredentialType.PIN) KeyboardType.NumberPassword else KeyboardType.Password),
            trailingIcon = {
              IconButton(onClick = { isCurrentVisible = !isCurrentVisible }) {
                Icon(
                  imageVector = if (isCurrentVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = null
                )
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("current_credential_input")
          )
          Spacer(modifier = Modifier.height(10.dp))
        }

        // New Secret
        OutlinedTextField(
          value = newSecret,
          onValueChange = {
            if (isPin) {
              if (it.all { char -> char.isDigit() }) newSecret = it
            } else {
              newSecret = it
            }
            localError = null
          },
          label = { Text("New ${selectedType.title}") },
          placeholder = { Text(if (isPin) "e.g., 123456" else "e.g., SecurePassphrase#2026") },
          singleLine = true,
          visualTransformation = if (isNewVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password),
          trailingIcon = {
            IconButton(onClick = { isNewVisible = !isNewVisible }) {
              Icon(
                imageVector = if (isNewVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null
              )
            }
          },
          supportingText = {
            Text(
              text = if (isPin) "Minimum $minLength digits (numeric)" else "Minimum $minLength characters (letters, digits, symbols)",
              style = MaterialTheme.typography.bodySmall,
              fontSize = 11.sp
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("new_credential_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Confirm New Secret
        OutlinedTextField(
          value = confirmSecret,
          onValueChange = {
            if (isPin) {
              if (it.all { char -> char.isDigit() }) confirmSecret = it
            } else {
              confirmSecret = it
            }
            localError = null
          },
          label = { Text("Confirm New ${selectedType.title}") },
          singleLine = true,
          visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password),
          trailingIcon = {
            IconButton(onClick = { isConfirmVisible = !isConfirmVisible }) {
              Icon(
                imageVector = if (isConfirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null
              )
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("confirm_credential_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (isConfigured && currentSecret.isBlank()) {
            localError = "Please enter your current ${currentType.title}."
            return@Button
          }
          if (newSecret.length < minLength) {
            localError = "${selectedType.title} must be at least $minLength characters."
            return@Button
          }
          if (isPin && !newSecret.all { it.isDigit() }) {
            localError = "PIN must contain digits only."
            return@Button
          }
          if (newSecret != confirmSecret) {
            localError = "New ${selectedType.title}s do not match."
            return@Button
          }
          val success = onSave(if (isConfigured) currentSecret else null, newSecret, selectedType)
          if (!success) {
            localError = "Failed to update credential. Check your current credential."
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = SecureVaultAmber),
        modifier = Modifier.testTag("save_master_credential_button")
      ) {
        Text("Save & Apply", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun SecureFileCard(
  item: SecureFileItem,
  onPreview: () -> Unit,
  onExport: () -> Unit,
  onDelete: () -> Unit
) {
  val dateFormatted = remember(item.dateAdded) {
    SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(item.dateAdded))
  }
  val sizeFormatted = remember(item.fileSizeBytes) {
    formatFileSize(item.fileSizeBytes)
  }
  val fileIcon = remember(item.mimeType) {
    getFileTypeIcon(item.mimeType)
  }

  val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) com.example.ui.theme.PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
    animationSpec = com.example.ui.theme.PaperTrailMotion.pressScaleSpec(),
    label = "press_scale"
  )

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(14.dp))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current,
        onClick = onPreview
      )
      .testTag("secure_file_card_${item.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // File Icon Box
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SecureVaultAmberContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = fileIcon,
          contentDescription = null,
          tint = SecureVaultOnAmberContainer,
          modifier = Modifier.size(24.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      // File Details
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.originalFileName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = sizeFormatted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = " • $dateFormatted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Actions
      IconButton(
        onClick = onExport,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FileDownload,
          contentDescription = "Export / Decrypt file",
          tint = SecureVaultAmber,
          modifier = Modifier.size(20.dp)
        )
      }

      val isMedia = item.mimeType.startsWith("video/") || item.mimeType.startsWith("audio/")
      IconButton(
        onClick = onPreview,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = if (isMedia) Icons.Default.PlayArrow else Icons.Default.Visibility,
          contentDescription = if (isMedia) "Play media" else "Preview file",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete file",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
private fun SecureFilePreviewDialog(
  preview: SecureVaultPreview,
  onExport: () -> Unit,
  onDismiss: () -> Unit
) {
  val item = preview.item
  val isImage = item.mimeType.startsWith("image/")
  val isText = item.mimeType.startsWith("text/") ||
      item.mimeType.contains("json") ||
      item.mimeType.contains("csv") ||
      item.mimeType.contains("xml") ||
      item.mimeType.contains("markdown")

  val imageBitmap = remember(preview.decryptedBytes) {
    if (isImage && preview.decryptedBytes != null) {
      decodeSafeSampledBitmap(preview.decryptedBytes)
    } else null
  }

  val textContent = remember(preview.decryptedBytes) {
    if (isText && preview.decryptedBytes != null) {
      try {
        String(preview.decryptedBytes, Charsets.UTF_8)
      } catch (e: Throwable) {
        "Error decoding text content"
      }
    } else null
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .border(1.dp, SecureVaultAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.EnhancedEncryption,
              contentDescription = null,
              tint = SecureVaultAmber,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = item.originalFileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = if (preview.isTooLargeToPreview) {
                  "${formatFileSize(item.fileSizeBytes)} • Encrypted Blob on Disk"
                } else {
                  "${formatFileSize(item.fileSizeBytes)} • Decrypted in RAM only"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SecureVaultAmber
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close preview")
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Area
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp, max = 380.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          if (preview.isTooLargeToPreview) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(16.dp)
            ) {
              Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = SecureVaultAmber
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "File Too Large to Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "File too large to preview (${formatFileSize(item.fileSizeBytes)}) — stored securely, but preview isn't available for files this size.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
              )
            }
          } else if (imageBitmap != null) {
            Image(
              bitmap = imageBitmap,
              contentDescription = item.originalFileName,
              modifier = Modifier.fillMaxSize()
            )
          } else if (textContent != null) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
            ) {
              Text(
                text = textContent,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(16.dp)
            ) {
              Icon(
                imageVector = getFileTypeIcon(item.mimeType),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = SecureVaultAmber
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Binary / Document File",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "MIME: ${item.mimeType}\nSize: ${formatFileSize(item.fileSizeBytes)}\nVerified intact in volatile memory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onExport,
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = SecureVaultAmber)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Export", color = SecureVaultAmber, fontWeight = FontWeight.Bold)
          }

          Button(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
          ) {
            Text(if (preview.isTooLargeToPreview) "Close" else "Close & Wipe")
          }
        }
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val kb = bytes / 1024.0
  val mb = kb / 1024.0
  val gb = mb / 1024.0
  return when {
    gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
    mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
    kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
    else -> "$bytes B"
  }
}

private fun getFileTypeIcon(mimeType: String): ImageVector {
  return when {
    mimeType.startsWith("image/") -> Icons.Default.Image
    mimeType.startsWith("video/") -> Icons.Default.VideoFile
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
    mimeType.startsWith("text/") || mimeType.contains("document") -> Icons.Default.Description
    mimeType.contains("zip") || mimeType.contains("tar") || mimeType.contains("compressed") -> Icons.Default.FolderZip
    else -> Icons.Default.InsertDriveFile
  }
}

private fun decodeSafeSampledBitmap(bytes: ByteArray, maxDim: Int = 1600): androidx.compose.ui.graphics.ImageBitmap? {
  return try {
    val boundsOptions = BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val width = boundsOptions.outWidth
    val height = boundsOptions.outHeight
    if (width <= 0 || height <= 0) return null

    var inSampleSize = 1
    if (height > maxDim || width > maxDim) {
      val halfHeight = height / 2
      val halfWidth = width / 2
      while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
        inSampleSize *= 2
      }
    }

    while ((width / inSampleSize) * (height / inSampleSize) * 4 > 30 * 1024 * 1024) {
      inSampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
      this.inSampleSize = inSampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    bitmap?.asImageBitmap()
  } catch (e: Throwable) {
    null
  }
}
