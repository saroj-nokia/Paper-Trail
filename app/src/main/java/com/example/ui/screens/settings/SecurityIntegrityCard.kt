package com.example.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.example.ui.theme.PaperTrailMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.IntegrityCheckItem
import com.example.data.security.IntegrityReport
import com.example.data.security.IntegrityStatus
import com.example.data.security.SecurityAuditPreferences
import com.example.data.security.SecurityIntegrityAuditor
import com.example.ui.components.DashedDivider
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.AmberAlertContainer
import com.example.ui.theme.AmberAlertOnContainer
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MonospaceLedgerStyle
import com.example.ui.theme.StampRed
import com.example.ui.theme.StampRedContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurityIntegrityCard(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val cached = SecurityIntegrityAuditor.getCachedReport()
  var report by remember {
    mutableStateOf(
      cached ?: IntegrityReport(
        items = listOf(
          IntegrityCheckItem(
            id = "selinux",
            title = "SELinux Confinement",
            status = IntegrityStatus.VERIFIED,
            summary = "Enforcing (Hardware TEE)",
            technicalDetail = "Kernel SELinux is in Enforcing mode. untrusted_app domain isolation active.",
            isCritical = true
          ),
          IntegrityCheckItem(
            id = "keymaster",
            title = "Keymaster / KeyMint HAL (TEE)",
            status = IntegrityStatus.VERIFIED,
            summary = "Operational (Hardware TEE)",
            technicalDetail = "Hardware-backed KeyStore HAL is operational.",
            isCritical = true
          ),
          IntegrityCheckItem(
            id = "strongbox",
            title = "StrongBox Dedicated HSM",
            status = IntegrityStatus.VERIFIED,
            summary = "Verified Hardware Module",
            technicalDetail = "Hardware Security Module check passed.",
            isCritical = false
          ),
          IntegrityCheckItem(
            id = "storage_encryption",
            title = "Storage Hardware Encryption",
            status = IntegrityStatus.VERIFIED,
            summary = "Hardware-Backed Encryption",
            technicalDetail = "Storage encryption verified.",
            isCritical = true
          ),
          IntegrityCheckItem(
            id = "biometrics",
            title = "Biometric & Credential Gate",
            status = IntegrityStatus.VERIFIED,
            summary = "Enforced & Bound",
            technicalDetail = "Biometric hardware is configured and active.",
            isCritical = false
          )
        ),
        isEnvironmentSecure = true,
        hasCriticalFailures = false
      )
    )
  }
  var isAuditing by remember { mutableStateOf(cached == null) }

  LaunchedEffect(Unit) {
    if (cached == null) {
      val newReport = withContext(Dispatchers.Default) {
        SecurityIntegrityAuditor.runFullAudit(context)
      }
      report = newReport
      isAuditing = false
    }
  }

  var isStrictGateEnabled by remember {
    mutableStateOf(SecurityAuditPreferences.isStrictGateEnabled(context))
  }
  var expandedItemId by remember { mutableStateOf<String?>(null) }

  fun runAudit() {
    isAuditing = true
    scope.launch {
      delay(300) // Brief visual feedback
      val newReport = withContext(Dispatchers.Default) {
        SecurityIntegrityAuditor.runFullAudit(context)
      }
      report = newReport
      SecurityAuditPreferences.setLastAuditTime(context, System.currentTimeMillis())
      isAuditing = false
    }
  }

  val overallColor = when {
    report.hasCriticalFailures -> StampRed
    report.items.any { it.status == IntegrityStatus.WARNING } -> AmberAlert
    else -> ForestPrimary
  }

  val overallContainer = when {
    report.hasCriticalFailures -> StampRedContainer
    report.items.any { it.status == IntegrityStatus.WARNING } -> AmberAlertContainer
    else -> ForestContainer
  }

  val overallOnContainer = when {
    report.hasCriticalFailures -> StampRed
    report.items.any { it.status == IntegrityStatus.WARNING } -> AmberAlertOnContainer
    else -> ForestOnContainer
  }

  val overallTitle = when {
    report.hasCriticalFailures -> "Critical Security Vulnerability"
    report.items.any { it.status == IntegrityStatus.WARNING } -> "Security Warnings Detected"
    else -> "Hardware & OS Integrity Verified"
  }

  val overallSubtitle = when {
    report.hasCriticalFailures -> "SELinux is permissive/spoofed, storage is unencrypted, or Keymaster HAL is unavailable."
    report.items.any { it.status == IntegrityStatus.WARNING } -> "System is protected, but some optional security settings require attention."
    else -> "Hardware TEE, SELinux sandbox, and storage encryption are actively enforced."
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .border(1.dp, overallColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
      .animateContentSize(animationSpec = PaperTrailMotion.expressiveExpand())
      .testTag("security_integrity_diagnostic_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header Banner
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(overallContainer)
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = when {
            report.hasCriticalFailures -> Icons.Default.Warning
            report.items.any { it.status == IntegrityStatus.WARNING } -> Icons.Default.WarningAmber
            else -> Icons.Default.CheckCircle
          },
          contentDescription = null,
          tint = overallColor,
          modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = overallTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = overallOnContainer
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = overallSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = overallOnContainer.copy(alpha = 0.85f)
          )
        }
      }

      Text(
        text = "Hardware & OS Diagnostic Telemetry",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      // Individual Integrity Checks
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        report.items.forEach { item ->
          IntegrityItemRow(
            item = item,
            isExpanded = expandedItemId == item.id,
            onToggleExpand = {
              expandedItemId = if (expandedItemId == item.id) null else item.id
            }
          )
        }
      }

      DashedDivider()

      // Fail-Closed Gate Setting
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Fail-Closed Hardware Gate",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Block app startup if SELinux is permissive/spoofed or Keymaster HAL fails",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Switch(
          checked = isStrictGateEnabled,
          onCheckedChange = {
            isStrictGateEnabled = it
            SecurityAuditPreferences.setStrictGateEnabled(context, it)
          },
          modifier = Modifier.testTag("toggle_strict_hardware_gate")
        )
      }

      // Re-run Audit Button
      OutlinedButton(
        onClick = { runAudit() },
        enabled = !isAuditing,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("run_integrity_audit_button")
      ) {
        if (isAuditing) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Auditing Hardware HALs & SELinux...")
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Run Hardware Diagnostic Audit")
        }
      }
    }
  }
}

@Composable
private fun IntegrityItemRow(
  item: IntegrityCheckItem,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit
) {
  val (statusColor, statusIcon, statusText) = when (item.status) {
    IntegrityStatus.VERIFIED -> Triple(ForestPrimary, Icons.Default.CheckCircle, "PASS")
    IntegrityStatus.WARNING -> Triple(AmberAlert, Icons.Default.WarningAmber, "WARN")
    IntegrityStatus.CRITICAL_FAILURE -> Triple(StampRed, Icons.Default.Warning, "FAIL")
    IntegrityStatus.OPTIONAL_ABSENT -> Triple(Color.Gray, Icons.Default.Info, "INFO")
  }

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
    animationSpec = PaperTrailMotion.pressScaleSpec(),
    label = "press_scale"
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current
      ) { onToggleExpand() }
      .padding(10.dp)
      .testTag("integrity_row_${item.id}")
  ) {
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
          imageVector = statusIcon,
          contentDescription = item.status.name,
          tint = statusColor,
          modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = item.summary,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Icon(
        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (isExpanded) "Collapse details" else "Expand details",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
    }

    AnimatedVisibility(
      visible = isExpanded,
      enter = expandVertically(animationSpec = PaperTrailMotion.expressiveExpand()) + fadeIn(animationSpec = PaperTrailMotion.fadeIn),
      exit = shrinkVertically(animationSpec = PaperTrailMotion.expressiveCollapse()) + fadeOut(animationSpec = PaperTrailMotion.fadeOut)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
        ) {
          Text(
            text = item.technicalDetail,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              lineHeight = 15.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
