package com.example.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.db.AppDatabase
import com.example.data.db.DatabasePassphraseManager
import com.example.data.security.BiometricAuthManager
import com.example.ui.components.DashedDivider
import com.example.ui.components.ReceiptPerforatedHeader
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberAlertContainer
import com.example.ui.theme.AmberAlertOnContainer
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.LocalBottomBarPadding
import com.example.ui.theme.LocalFrostedGlassEnabled
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.LocalSetFrostedGlassEnabled
import com.example.ui.theme.MonospaceLedgerStyle
import com.example.ui.theme.StampRed
import com.example.ui.theme.frostedGlassSource
import com.example.ui.theme.frostedGlassTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: VaultViewModel,
  onLockVault: () -> Unit,
  onNavigateToTutorial: () -> Unit
) {
  val context = LocalContext.current
  val authManager = viewModel.authManager
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var isLockEnabled by remember { mutableStateOf(authManager.isLockConfigured) }

  // Check notification permission state (Android 13+ / API 33+)
  var hasNotificationPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotificationPermission = granted
    scope.launch {
      if (granted) {
        snackbarHostState.showSnackbar("Notification permissions enabled for reminders.")
      } else {
        snackbarHostState.showSnackbar("Notifications permission denied. Reminders will be limited.")
      }
    }
  }

  val isEncryptionFallback = AppDatabase.isEncryptionFallbackActive || DatabasePassphraseManager.isFallbackMode
  val frostedGlassEnabled = LocalFrostedGlassEnabled.current
  val setFrostedGlassEnabled = LocalSetFrostedGlassEnabled.current
  val hazeState = LocalHazeState.current

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text("Security & Privacy", fontWeight = FontWeight.Bold)
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = if (frostedGlassEnabled) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
          } else {
            MaterialTheme.colorScheme.background
          }
        ),
        modifier = if (frostedGlassEnabled) {
          Modifier.frostedGlassTopBar(hazeState, enabled = true)
        } else {
          Modifier
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    val bottomBarPadding = LocalBottomBarPadding.current
    val topPadding = if (frostedGlassEnabled) 0.dp else paddingValues.calculateTopPadding()
    val extraTopPadding = if (frostedGlassEnabled) paddingValues.calculateTopPadding() else 0.dp

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = topPadding)
        .frostedGlassSource(hazeState, enabled = frostedGlassEnabled)
        .verticalScroll(rememberScrollState())
        .padding(
          start = 16.dp,
          end = 16.dp,
          top = 12.dp + extraTopPadding,
          bottom = 16.dp + bottomBarPadding
        ),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 0. Fallback Warning Banner if unencrypted storage fallback is active
      if (isEncryptionFallback) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, AmberAlert, RoundedCornerShape(12.dp))
            .testTag("encryption_fallback_warning_card"),
          colors = CardDefaults.cardColors(containerColor = AmberAlertContainer)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AmberAlertOnContainer,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Unencrypted Storage Fallback Active",
                style = MaterialTheme.typography.titleMedium,
                color = AmberAlertOnContainer,
                fontWeight = FontWeight.Bold
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "SQLCipher hardware keystore encryption could not be loaded on this runtime environment. The vault is operating in local unencrypted compatibility mode. All data remains strictly on-device with zero cloud connections.",
              style = MaterialTheme.typography.bodySmall,
              color = AmberAlertOnContainer.copy(alpha = 0.9f)
            )
          }
        }
      }

      // 1. Hardware & OS Security Integrity Diagnostic Card
      SecurityIntegrityCard()

      // 1. Zero Cloud Commitment Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, ForestPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
          .testTag("privacy_commitment_card"),
        colors = CardDefaults.cardColors(containerColor = ForestContainer)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = ForestPrimary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "100% Offline Vault Guarantee",
              style = MaterialTheme.typography.titleMedium,
              color = ForestOnContainer,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "• Zero Cloud Synchronization\n• Zero Third-Party Analytics or Crash Trackers\n• Zero Bank Account Linking\n• ${if (isEncryptionFallback) "Standard SQLite Local Storage (Compatibility Mode)" else "256-bit AES SQLCipher On-Device Encryption"}\n• On-Device ML Kit OCR Processing",
            style = MaterialTheme.typography.bodyMedium,
            color = ForestOnContainer.copy(alpha = 0.9f)
          )
        }
      }

      // 2. Biometric Security Settings
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Vault Lock",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Biometric / PIN Screen Lock", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
              Text("Require biometric authentication or device PIN to access receipts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Switch(
              checked = isLockEnabled,
              onCheckedChange = {
                isLockEnabled = it
                authManager.setLockConfigured(it)
              },
              modifier = Modifier.testTag("toggle_biometric_lock")
            )
          }

          DashedDivider()

          Button(
            onClick = onLockVault,
            modifier = Modifier.fillMaxWidth().testTag("lock_now_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
          ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lock Vault Now")
          }
        }
      }

      // 3. Background Reminders & Permissions
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Notifications & Reminders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Text(
            text = "Reminders alert you before warranties expire and subscriptions renew. Scheduled via Android WorkManager.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Notification Permission",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = if (hasNotificationPermission) "Enabled (Alerts will post)" else "Disabled (Permission required)",
                  style = MaterialTheme.typography.bodySmall,
                  color = if (hasNotificationPermission) ForestPrimary else StampRed
                )
              }

              if (!hasNotificationPermission) {
                Button(
                  onClick = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier.testTag("grant_notification_permission_button")
                ) {
                  Text("Enable")
                }
              } else {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Permission Granted",
                  tint = ForestPrimary,
                  modifier = Modifier.size(24.dp)
                )
              }
            }
          }

          OutlinedButton(
            onClick = {
              viewModel.triggerReminderCheck()
              scope.launch {
                snackbarHostState.showSnackbar("Background reminder check triggered.")
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("test_reminders_button")
          ) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Test Reminder Check Now")
          }
        }
      }

      // 4. Appearance Settings
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
          .testTag("appearance_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Frosted Glass Effect",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Adds a blurred glass look to bars and sheets. Off by default — enable if you like the look; it uses a bit more GPU.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
              checked = frostedGlassEnabled,
              onCheckedChange = { isChecked ->
                setFrostedGlassEnabled(isChecked)
              },
              modifier = Modifier.testTag("toggle_frosted_glass")
            )
          }
        }
      }

      // 5. Tutorial & Walkthrough
      Card(
        onClick = onNavigateToTutorial,
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
          .testTag("how_to_use_row"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.HelpOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "How to use Paper Trail",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Replay the walkthrough tutorial",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      // 5. App Info
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Paper Trail v1.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Local-First Receipt, Warranty & Subscription Vault", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}
