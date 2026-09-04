package com.example.ui.screens.detail

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import com.example.ui.components.CategoryBadge
import com.example.ui.components.DashedDivider
import com.example.ui.components.ReceiptPerforatedHeader
import com.example.ui.components.SubscriptionBadge
import com.example.ui.components.WarrantyBadge
import com.example.ui.screens.vault.CATEGORY_OPTIONS
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MonospaceAmountStyle
import com.example.ui.theme.MonospaceLedgerStyle
import com.example.ui.theme.StampRed
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemDetailEditScreen(
  itemId: Long,
  viewModel: VaultViewModel,
  onNavigateBack: () -> Unit
) {
  val context = LocalContext.current
  val allItems by viewModel.allItems.collectAsStateWithLifecycle()
  val item = allItems.find { it.id == itemId }

  var isEditing by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showFullPhotoDialog by remember { mutableStateOf(false) }

  // Editable Form State
  var storeName by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("General") }
  var purchaseDateMs by remember { mutableStateOf(0L) }
  var notes by remember { mutableStateOf("") }
  var isWarranty by remember { mutableStateOf(false) }
  var warrantyExpirationDateMs by remember { mutableStateOf(0L) }
  var isSubscription by remember { mutableStateOf(false) }
  var subscriptionCycle by remember { mutableStateOf(SubscriptionCycle.MONTHLY) }
  var subscriptionNextRenewalDateMs by remember { mutableStateOf(0L) }
  var subscriptionActive by remember { mutableStateOf(true) }
  var reminderDays by remember { mutableStateOf(7) }

  LaunchedEffect(item) {
    if (item != null) {
      storeName = item.storeName
      amountStr = String.format(Locale.US, "%.2f", item.amount)
      category = item.category
      purchaseDateMs = item.purchaseDate
      notes = item.notes ?: ""
      isWarranty = item.isWarranty
      warrantyExpirationDateMs = item.warrantyExpirationDate ?: (System.currentTimeMillis() + 31536000000L)
      isSubscription = item.isSubscription
      subscriptionCycle = item.cycleEnum ?: SubscriptionCycle.MONTHLY
      subscriptionNextRenewalDateMs = item.subscriptionNextRenewalDate ?: (System.currentTimeMillis() + 2592000000L)
      subscriptionActive = item.subscriptionActive
      reminderDays = item.reminderDaysBefore
    }
  }

  val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
  val currencyFmt = NumberFormat.getCurrencyInstance(Locale.US)

  fun showDatePicker(initialMs: Long, onDateSelected: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = if (initialMs > 0) initialMs else System.currentTimeMillis() }
    DatePickerDialog(
      context,
      { _, year, month, day ->
        val selected = Calendar.getInstance().apply {
          set(Calendar.YEAR, year)
          set(Calendar.MONTH, month)
          set(Calendar.DAY_OF_MONTH, day)
        }
        onDateSelected(selected.timeInMillis)
      },
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH),
      cal.get(Calendar.DAY_OF_MONTH)
    ).show()
  }

  if (item == null) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Item Detail") },
          navigationIcon = {
            IconButton(onClick = onNavigateBack) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        )
      }
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text("Item not found.")
      }
    }
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = if (isEditing) "Edit Record" else "Receipt Ledger",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          if (!isEditing) {
            IconButton(onClick = { isEditing = true }, modifier = Modifier.testTag("edit_item_button")) {
              Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.testTag("delete_item_button")) {
              Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StampRed)
            }
          } else {
            IconButton(
              onClick = {
                val parsedAmount = amountStr.toDoubleOrNull() ?: item.amount
                val updated = item.copy(
                  storeName = storeName.ifBlank { item.storeName },
                  amount = parsedAmount,
                  category = category,
                  purchaseDate = purchaseDateMs,
                  notes = notes.ifBlank { null },
                  isWarranty = isWarranty,
                  warrantyExpirationDate = if (isWarranty) warrantyExpirationDateMs else null,
                  isSubscription = isSubscription,
                  subscriptionCycle = if (isSubscription) subscriptionCycle.name else null,
                  subscriptionNextRenewalDate = if (isSubscription) subscriptionNextRenewalDateMs else null,
                  subscriptionActive = subscriptionActive,
                  reminderDaysBefore = reminderDays
                )
                viewModel.updateItem(updated)
                isEditing = false
              },
              modifier = Modifier.testTag("save_edit_button")
            ) {
              Icon(Icons.Default.Check, contentDescription = "Save")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Photo Preview Card
      if (!item.imagePath.isNullOrEmpty() && File(item.imagePath).exists()) {
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
            .clickable(
              interactionSource = interactionSource,
              indication = androidx.compose.foundation.LocalIndication.current,
              onClick = { showFullPhotoDialog = true }
            )
            .testTag("receipt_image_preview"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp),
            contentAlignment = Alignment.Center
          ) {
            AsyncImage(
              model = File(item.imagePath),
              contentDescription = "Full receipt capture",
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize()
            )

            Box(
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Tap to Zoom", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }

      // 2. Receipt Details Ticket Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          ReceiptPerforatedHeader()

          Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isEditing) {
              // Read-only view
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.storeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Purchased on ${dateFormat.format(Date(item.purchaseDate))}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Text(
                  text = currencyFmt.format(item.amount),
                  style = MonospaceAmountStyle.copy(fontSize = 24.sp),
                  color = MaterialTheme.colorScheme.primary
                )
              }

              DashedDivider()

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                CategoryBadge(category = item.category)
                if (item.isWarranty) WarrantyBadge(item = item)
                if (item.isSubscription) SubscriptionBadge(item = item)
              }

              if (!item.notes.isNullOrBlank()) {
                DashedDivider()
                Column {
                  Text("Notes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(item.notes, style = MaterialTheme.typography.bodyMedium)
                }
              }
            } else {
              // Editable Fields
              OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                label = { Text("Store / Merchant Name") },
                modifier = Modifier.fillMaxWidth().testTag("edit_store_name"),
                singleLine = true
              )

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = amountStr,
                  onValueChange = { amountStr = it },
                  label = { Text("Total Amount") },
                  modifier = Modifier.weight(1f).testTag("edit_amount"),
                  singleLine = true
                )

                OutlinedButton(
                  onClick = { showDatePicker(purchaseDateMs) { purchaseDateMs = it } },
                  modifier = Modifier.align(Alignment.CenterVertically).height(56.dp)
                ) {
                  Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(dateFormat.format(Date(purchaseDateMs)), style = MaterialTheme.typography.bodySmall)
                }
              }

              Text("Category", style = MaterialTheme.typography.labelMedium)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CATEGORY_OPTIONS.filter { it != "All" }.forEach { cat ->
                  FilterChip(
                    selected = category.equals(cat, ignoreCase = true),
                    onClick = { category = cat },
                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                  )
                }
              }

              OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
              )
            }
          }
        }
      }

      // 3. Warranty Section Card
      if (item.isWarranty || isEditing) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Warranty Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              }

              if (isEditing) {
                Switch(checked = isWarranty, onCheckedChange = { isWarranty = it })
              }
            }

            if (item.isWarranty && !isEditing) {
              val days = item.daysUntilWarrantyExpires()
              Text(
                text = "Expires on: ${dateFormat.format(Date(item.warrantyExpirationDate ?: 0))}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
              )
              Text(
                text = if (days != null && days >= 0) "$days days remaining" else "Expired",
                style = MaterialTheme.typography.bodySmall,
                color = if (days != null && days > 7) ForestPrimary else StampRed,
                fontWeight = FontWeight.Bold
              )
            } else if (isWarranty && isEditing) {
              OutlinedButton(
                onClick = { showDatePicker(warrantyExpirationDateMs) { warrantyExpirationDateMs = it } },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Expires: ${dateFormat.format(Date(warrantyExpirationDateMs))}")
              }
            }
          }
        }
      }

      // 4. Subscription Section Card
      if (item.isSubscription || isEditing) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subscription Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              }

              if (isEditing) {
                Switch(checked = isSubscription, onCheckedChange = { isSubscription = it })
              }
            }

            if (item.isSubscription && !isEditing) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("Cycle: ${item.cycleEnum?.label ?: "Monthly"}", style = MaterialTheme.typography.bodyMedium)
                  if (item.subscriptionNextRenewalDate != null) {
                    Text("Next Renewal: ${dateFormat.format(Date(item.subscriptionNextRenewalDate))}", style = MaterialTheme.typography.bodySmall)
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(if (item.subscriptionActive) "Active" else "Paused", style = MaterialTheme.typography.labelSmall)
                  Spacer(modifier = Modifier.width(8.dp))
                  Switch(
                    checked = item.subscriptionActive,
                    onCheckedChange = { viewModel.toggleSubscriptionActive(item) }
                  )
                }
              }
            }
          }
        }
      }

      // 5. Raw OCR Text Sheet (if present)
      if (!item.ocrRawText.isNullOrBlank()) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("On-Device OCR Extracted Text", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = item.ocrRawText,
              style = MonospaceLedgerStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 10
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  // Delete Confirmation Dialog
  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete Record") },
      text = { Text("Are you sure you want to delete this receipt and remove its encrypted photo from your vault? This cannot be undone.") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteItem(item)
            showDeleteDialog = false
            onNavigateBack()
          },
          colors = ButtonDefaults.buttonColors(containerColor = StampRed)
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Full Screen Photo Dialog
  if (showFullPhotoDialog && !item.imagePath.isNullOrEmpty()) {
    Dialog(
      onDismissRequest = { showFullPhotoDialog = false },
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(androidx.compose.ui.graphics.Color.Black)
      ) {
        AsyncImage(
          model = File(item.imagePath),
          contentDescription = "Full zoom receipt",
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize()
        )

        IconButton(
          onClick = { showFullPhotoDialog = false },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
        }
      }
    }
  }
}
