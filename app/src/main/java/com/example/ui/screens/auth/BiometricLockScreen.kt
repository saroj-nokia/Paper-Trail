package com.example.ui.screens.auth

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.data.security.BiometricAuthManager
import com.example.ui.components.DashedDivider
import com.example.ui.components.ReceiptPerforatedHeader
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MonospaceLedgerStyle

@Composable
fun BiometricLockScreen(
  authManager: BiometricAuthManager,
  onUnlocked: () -> Unit
) {
  val context = LocalContext.current
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun triggerAuth() {
    val activity = context as? FragmentActivity
    if (activity != null) {
      authManager.promptBiometric(
        activity = activity,
        title = "Unlock Paper Trail",
        subtitle = "Scan biometric or enter device PIN/Password to access encrypted vault",
        onSuccess = {
          errorMessage = null
          onUnlocked()
        },
        onError = { err ->
          errorMessage = err
        }
      )
    } else {
      // Fallback if not inside FragmentActivity
      authManager.unlockDirectly()
      onUnlocked()
    }
  }

  LaunchedEffect(Unit) {
    triggerAuth()
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
          .testTag("biometric_lock_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          ReceiptPerforatedHeader()

          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            // Icon
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ForestContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = ForestPrimary,
                modifier = Modifier.size(36.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "Paper Trail Vault",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = "ENCRYPTED LEDGER AT REST",
              style = MonospaceLedgerStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text(
              text = "Your receipts, warranties, and subscription credentials are encrypted locally on this device.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (errorMessage != null) {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
              onClick = { triggerAuth() },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("unlock_biometric_button"),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Default.Fingerprint, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Unlock Vault", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
              onClick = { triggerAuth() },
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("unlock_pin_fallback_button"),
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Use Device PIN / Password")
            }
          }
        }
      }
    }
  }
}
