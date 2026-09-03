package com.example.ui.screens.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.LocalFrostedGlassEnabled
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.frostedGlassSource
import com.example.ui.theme.frostedGlassTopBar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.AppDatabase
import com.example.data.db.DatabasePassphraseManager
import com.example.data.model.VaultItem
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DashedDivider
import com.example.ui.components.PerforatedReceiptCard
import com.example.ui.components.ReceiptPerforatedHeader
import com.example.ui.components.WarrantyStatusBar
import com.example.ui.screens.vault.VaultTab
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberAlertContainer
import com.example.ui.theme.AmberAlertOnContainer
import com.example.ui.theme.BlueSubscription
import com.example.ui.theme.BlueSubscriptionContainer
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MintLedger
import com.example.ui.theme.MonospaceAmountStyle
import com.example.ui.theme.PurpleWarranty
import com.example.ui.theme.PurpleWarrantyContainer
import com.example.ui.theme.StampRed
import com.example.ui.theme.StampRedContainer
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: VaultViewModel,
  onNavigateToCapture: () -> Unit,
  onNavigateToItemDetail: (Long) -> Unit,
  onNavigateToVault: (VaultTab) -> Unit,
  onLockVault: () -> Unit
) {
  val context = LocalContext.current
  val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

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
      }
    }
  }

  val isEncryptionFallback = AppDatabase.isEncryptionFallbackActive || DatabasePassphraseManager.isFallbackMode
  val frostedGlassEnabled = LocalFrostedGlassEnabled.current
  val hazeState = LocalHazeState.current

  val currencyFmt = NumberFormat.getCurrencyInstance(Locale.US)
  val monthlySubFormatted = currencyFmt.format(stats.monthlySubscriptionCost)

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.ReceiptLong,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Paper Trail",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        },
        actions = {
          IconButton(
            onClick = {
              viewModel.triggerReminderCheck()
              scope.launch {
                snackbarHostState.showSnackbar("Background reminder check triggered.")
              }
            },
            modifier = Modifier.testTag("notification_check_button")
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "Trigger Reminder Check"
            )
          }
          IconButton(
            onClick = onLockVault,
            modifier = Modifier.testTag("lock_vault_button")
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Lock Vault"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = if (frostedGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.background
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
    val topPadding = if (frostedGlassEnabled) 0.dp else paddingValues.calculateTopPadding()
    val extraTopPadding = if (frostedGlassEnabled) paddingValues.calculateTopPadding() else 0.dp
    val extraBottomPadding = if (frostedGlassEnabled) 80.dp else 0.dp

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = topPadding)
        .frostedGlassSource(hazeState, enabled = frostedGlassEnabled),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 12.dp + extraTopPadding,
        bottom = 12.dp + extraBottomPadding
      ),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 0a. Notification permission banner if on Android 13+ and not granted
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .testTag("notification_permission_banner"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "Enable Reminders",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                  Text(
                    text = "Get alerted before warranties expire or subscriptions renew",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                  )
                }
              }

              Spacer(modifier = Modifier.width(8.dp))

              Button(
                onClick = {
                  notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("enable_notifications_banner_button")
              ) {
                Text("Enable", style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }

      // 0b. Fallback warning if unencrypted storage is active
      if (isEncryptionFallback) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, AmberAlert, RoundedCornerShape(12.dp))
              .testTag("dashboard_encryption_fallback_warning"),
            colors = CardDefaults.cardColors(containerColor = AmberAlertContainer)
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAlertOnContainer, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Unencrypted Storage Mode: Local SQLite fallback active.",
                style = MaterialTheme.typography.bodySmall,
                color = AmberAlertOnContainer,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // 1. Subscription Monthly Total Ledger Hero Card
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("subscription_hero_card"),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            ReceiptPerforatedHeader()

            Column(modifier = Modifier.padding(20.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(CircleShape)
                      .background(ForestContainer),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Autorenew,
                      contentDescription = null,
                      tint = ForestOnContainer,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "MONTHLY COMMITMENT",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      letterSpacing = 1.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "${stats.activeSubscriptionCount} active subscriptions",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Text(
                  text = "OFFLINE VAULT",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = FontFamily.Monospace,
                  color = ForestPrimary,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(ForestContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }

              Spacer(modifier = Modifier.height(16.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column {
                  Text(
                    text = monthlySubFormatted,
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                  )
                  Text(
                    text = "Projected / month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Button(
                  onClick = { onNavigateToVault(VaultTab.SUBSCRIPTIONS) },
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("view_subs_button")
                ) {
                  Text("View All", style = MaterialTheme.typography.labelMedium)
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
              }
            }
          }
        }
      }

      // 2. Quick Action Grid
      item {
        Text(
          text = "Quick Actions",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          QuickActionButton(
            title = "Scan Receipt",
            subtitle = "On-Device OCR",
            icon = Icons.Default.CameraAlt,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onNavigateToCapture,
            modifier = Modifier.weight(1f).testTag("quick_scan_button")
          )

          QuickActionButton(
            title = "All Ledger",
            subtitle = "${stats.activeSubscriptionCount + stats.activeWarrantyCount} items",
            icon = Icons.Default.ReceiptLong,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { onNavigateToVault(VaultTab.ALL) },
            modifier = Modifier.weight(1f).testTag("quick_vault_button")
          )
        }
      }

      // 3. Expiring Warranties Alert Card (if any)
      if (stats.expiringWarranties.isNotEmpty()) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, AmberAlert.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .testTag("expiring_warranties_card"),
            colors = CardDefaults.cardColors(containerColor = AmberAlertContainer)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AmberAlertOnContainer,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Warranty Alerts (${stats.expiringWarranties.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberAlertOnContainer,
                    fontWeight = FontWeight.Bold
                  )
                }

                Text(
                  text = "Expiring Soon",
                  style = MaterialTheme.typography.labelSmall,
                  color = AmberAlertOnContainer,
                  fontWeight = FontWeight.SemiBold
                )
              }

              Spacer(modifier = Modifier.height(10.dp))

              stats.expiringWarranties.take(3).forEach { item ->
                val days = item.daysUntilWarrantyExpires() ?: 0
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigateToItemDetail(item.id) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column {
                    Text(
                      text = item.storeName,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = AmberAlertOnContainer
                    )
                    Text(
                      text = item.category,
                      style = MaterialTheme.typography.bodySmall,
                      color = AmberAlertOnContainer.copy(alpha = 0.8f)
                    )
                  }

                  Text(
                    text = if (days <= 0L) "Expires Today" else "in ${days}d",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = StampRed
                  )
                }
              }
            }
          }
        }
      }

      // 4. Upcoming Subscription Renewals
      if (stats.upcomingRenewals.isNotEmpty()) {
        item {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .border(1.dp, BlueSubscription.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
              .testTag("upcoming_renewals_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = BlueSubscription,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Upcoming Renewals",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                  )
                }

                Text(
                  text = "${stats.upcomingRenewals.size} due soon",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Spacer(modifier = Modifier.height(10.dp))

              stats.upcomingRenewals.take(4).forEach { item ->
                val days = item.daysUntilSubscriptionRenews() ?: 0
                val costStr = currencyFmt.format(item.amount)

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigateToItemDetail(item.id) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = item.storeName,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = "Renews in ${if (days == 0L) "today" else "${days} days"} (${item.cycleEnum?.label ?: "Monthly"})",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }

                  Text(
                    text = costStr,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                }

                DashedDivider()
              }
            }
          }
        }
      }

      // 5. Visual Expense & Subscription Breakdown Chart (Native Compose Canvas)
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .testTag("charts_summary_card"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Subscription Spend by Category",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            CategoryDonutChart(
              slices = stats.subscriptionCategorySlices,
              centerLabel = "Monthly",
              centerValue = monthlySubFormatted
            )

            Spacer(modifier = Modifier.height(20.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(16.dp))

            WarrantyStatusBar(
              activeCount = stats.activeWarrantyCount,
              expiringCount = stats.expiringWarrantyCount,
              expiredCount = stats.expiredWarrantyCount
            )
          }
        }
      }

      // Bottom padding for scroll
      item {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
fun QuickActionButton(
  title: String,
  subtitle: String,
  icon: ImageVector,
  backgroundColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) com.example.ui.theme.PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
    animationSpec = com.example.ui.theme.PaperTrailMotion.pressScaleSpec(),
    label = "press_scale"
  )

  Card(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(12.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current,
        onClick = onClick
      ),
    colors = CardDefaults.cardColors(containerColor = backgroundColor)
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(contentColor.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(22.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = contentColor,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = contentColor.copy(alpha = 0.8f)
        )
      }
    }
  }
}
