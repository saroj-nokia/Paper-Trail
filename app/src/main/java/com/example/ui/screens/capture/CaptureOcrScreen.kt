package com.example.ui.screens.capture

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubscriptionCycle
import com.example.data.ocr.OcrExtractedResult
import com.example.data.ocr.ReceiptOcrProcessor
import com.example.data.security.ImageFileManager
import com.example.ui.components.DashedDivider
import com.example.ui.screens.vault.CATEGORY_OPTIONS
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.MonospaceAmountStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureOcrScreen(
  viewModel: VaultViewModel,
  onNavigateBack: () -> Unit,
  onSaved: (Long) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isProcessingOcr by remember { mutableStateOf(false) }
  var ocrCompleted by remember { mutableStateOf(false) }
  var ocrMessage by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      if (capturedBitmap?.isRecycled == false) {
        capturedBitmap?.recycle()
      }
    }
  }

  // Form Fields
  var storeName by remember { mutableStateOf("") }
  var amountStr by remember { mutableStateOf("") }
  var currency by remember { mutableStateOf("$") }
  var category by remember { mutableStateOf("General") }
  var purchaseDateMs by remember { mutableStateOf(System.currentTimeMillis()) }
  var notes by remember { mutableStateOf("") }
  var rawOcrText by remember { mutableStateOf<String?>(null) }

  // Warranty Tracker Settings
  var isWarranty by remember { mutableStateOf(false) }
  var warrantyExpirationDateMs by remember {
    val cal = Calendar.getInstance()
    cal.add(Calendar.YEAR, 1)
    mutableStateOf(cal.timeInMillis)
  }

  // Subscription Tracker Settings
  var isSubscription by remember { mutableStateOf(false) }
  var subscriptionCycle by remember { mutableStateOf(SubscriptionCycle.MONTHLY) }
  var nextRenewalDateMs by remember {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, 1)
    mutableStateOf(cal.timeInMillis)
  }

  // Notification Reminder Offset
  var reminderDays by remember { mutableStateOf(7) }

  val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

  // Temp Camera URI
  var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

  // Launchers
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture()
  ) { success ->
    if (success && tempCameraUri != null) {
      scope.launch {
        isProcessingOcr = true
        ocrMessage = null
        val bitmap = ImageFileManager.decodeSampledBitmap(context, tempCameraUri!!)
        if (bitmap != null) {
          if (capturedBitmap?.isRecycled == false) {
            capturedBitmap?.recycle()
          }
          capturedBitmap = bitmap
          val ocrResult = ReceiptOcrProcessor.processImage(bitmap)
          if (ocrResult.isSuccessful && ocrResult.rawText.isNotBlank()) {
            if (ocrResult.storeName.isNotBlank()) storeName = ocrResult.storeName
            if (ocrResult.amount > 0.0) amountStr = String.format(Locale.US, "%.2f", ocrResult.amount)
            purchaseDateMs = ocrResult.purchaseDate
            category = ocrResult.suggestedCategory
            rawOcrText = ocrResult.rawText
            ocrCompleted = true
            ocrMessage = null
          } else {
            ocrCompleted = false
            ocrMessage = "Couldn't read text from this receipt — please enter details manually."
          }
        } else {
          ocrCompleted = false
          ocrMessage = "Unable to load captured photo. Please try again."
        }
        isProcessingOcr = false
      }
    }
  }

  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      scope.launch {
        isProcessingOcr = true
        ocrMessage = null
        val bitmap = ImageFileManager.decodeSampledBitmap(context, uri)
        if (bitmap != null) {
          if (capturedBitmap?.isRecycled == false) {
            capturedBitmap?.recycle()
          }
          capturedBitmap = bitmap
          val ocrResult = ReceiptOcrProcessor.processImage(bitmap)
          if (ocrResult.isSuccessful && ocrResult.rawText.isNotBlank()) {
            if (ocrResult.storeName.isNotBlank()) storeName = ocrResult.storeName
            if (ocrResult.amount > 0.0) amountStr = String.format(Locale.US, "%.2f", ocrResult.amount)
            purchaseDateMs = ocrResult.purchaseDate
            category = ocrResult.suggestedCategory
            rawOcrText = ocrResult.rawText
            ocrCompleted = true
            ocrMessage = null
          } else {
            ocrCompleted = false
            ocrMessage = "Couldn't read text from this receipt — please enter details manually."
          }
        } else {
          ocrCompleted = false
          ocrMessage = "Unable to load selected photo. Please try again."
        }
        isProcessingOcr = false
      }
    }
  }

  fun showDatePicker(initialMs: Long, onDateSelected: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMs }
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Scan Receipt OCR",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Photo Capture / Gallery Actions
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          if (capturedBitmap != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              Image(
                bitmap = capturedBitmap!!.asImageBitmap(),
                contentDescription = "Captured receipt preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
              )

              if (ocrCompleted) {
                Box(
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ForestContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = ForestOnContainer, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("OCR Extracted", style = MaterialTheme.typography.labelSmall, color = ForestOnContainer, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))
          }

          if (ocrMessage != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
              Text(
                text = ocrMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }

          if (isProcessingOcr) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 12.dp)
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Text("Downsampling & recognizing text on-device...", style = MaterialTheme.typography.bodyMedium)
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = {
                  val uri = ImageFileManager.createTempImageUri(context)
                  tempCameraUri = uri
                  cameraLauncher.launch(uri)
                },
                modifier = Modifier.weight(1f).testTag("camera_capture_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (capturedBitmap == null) "Take Photo" else "Retake")
              }

              OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f).testTag("gallery_picker_button")
              ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick Image")
              }
            }
          }
        }
      }

      // 2. Extracted / Editable Receipt Form
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Receipt Details (Review & Edit)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          // Store Name
          OutlinedTextField(
            value = storeName,
            onValueChange = { storeName = it },
            label = { Text("Store / Merchant Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_store_name"),
            shape = RoundedCornerShape(8.dp)
          )

          // Amount and Currency
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = amountStr,
              onValueChange = { amountStr = it },
              label = { Text("Total Amount") },
              placeholder = { Text("0.00") },
              leadingIcon = { Text("$", style = MonospaceAmountStyle, modifier = Modifier.padding(start = 12.dp)) },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("input_amount"),
              shape = RoundedCornerShape(8.dp)
            )

            // Purchase Date Picker Button
            OutlinedButton(
              onClick = {
                showDatePicker(purchaseDateMs) { purchaseDateMs = it }
              },
              modifier = Modifier
                .align(Alignment.CenterVertically)
                .height(56.dp)
                .testTag("button_purchase_date"),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(dateFormat.format(Date(purchaseDateMs)), style = MaterialTheme.typography.bodySmall)
            }
          }

          // Category Selector
          Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            CATEGORY_OPTIONS.filter { it != "All" }.forEach { cat ->
              FilterChip(
                selected = category.equals(cat, ignoreCase = true),
                onClick = { category = cat },
                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
              )
            }
          }

          // Notes
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes / Item Descriptions (Optional)") },
            modifier = Modifier.fillMaxWidth().testTag("input_notes"),
            shape = RoundedCornerShape(8.dp),
            maxLines = 3
          )
        }
      }

      // 3. Warranty Tracking Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
          .animateContentSize(animationSpec = com.example.ui.theme.PaperTrailMotion.expressiveExpand()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text("Track as Warranty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Monitor expiration and claims", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }

            Switch(
              checked = isWarranty,
              onCheckedChange = { isWarranty = it },
              modifier = Modifier.testTag("switch_warranty")
            )
          }

          if (isWarranty) {
            Spacer(modifier = Modifier.height(12.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text("Warranty Expiration Date", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedButton(
                onClick = {
                  showDatePicker(warrantyExpirationDateMs) { warrantyExpirationDateMs = it }
                },
                modifier = Modifier.weight(1f).testTag("button_warranty_date"),
                shape = RoundedCornerShape(8.dp)
              ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(dateFormat.format(Date(warrantyExpirationDateMs)))
              }

              // Quick preset buttons (1 Year, 2 Years)
              OutlinedButton(
                onClick = {
                  val cal = Calendar.getInstance().apply { timeInMillis = purchaseDateMs }
                  cal.add(Calendar.YEAR, 1)
                  warrantyExpirationDateMs = cal.timeInMillis
                },
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("+1 Year", style = MaterialTheme.typography.labelSmall)
              }

              OutlinedButton(
                onClick = {
                  val cal = Calendar.getInstance().apply { timeInMillis = purchaseDateMs }
                  cal.add(Calendar.YEAR, 2)
                  warrantyExpirationDateMs = cal.timeInMillis
                },
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("+2 Years", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }

      // 4. Subscription Tracking Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
          .animateContentSize(animationSpec = com.example.ui.theme.PaperTrailMotion.expressiveExpand()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text("Recurring Subscription", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Track renewal cycles and billing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }

            Switch(
              checked = isSubscription,
              onCheckedChange = { isSubscription = it },
              modifier = Modifier.testTag("switch_subscription")
            )
          }

          if (isSubscription) {
            Spacer(modifier = Modifier.height(12.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text("Renewal Cycle", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              SubscriptionCycle.values().forEach { cycle ->
                FilterChip(
                  selected = subscriptionCycle == cycle,
                  onClick = { subscriptionCycle = cycle },
                  label = { Text(cycle.label, style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier.weight(1f),
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Next Charge Date", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
              onClick = {
                showDatePicker(nextRenewalDateMs) { nextRenewalDateMs = it }
              },
              modifier = Modifier.fillMaxWidth().testTag("button_renewal_date"),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(dateFormat.format(Date(nextRenewalDateMs)))
            }
          }
        }
      }

      // 5. Notification Reminder Offset Card
      if (isWarranty || isSubscription) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Reminder Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Notify me before expiration/renewal:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(1, 3, 7, 14, 30).forEach { days ->
                FilterChip(
                  selected = reminderDays == days,
                  onClick = { reminderDays = days },
                  label = { Text("${days}d prior", style = MaterialTheme.typography.labelSmall) }
                )
              }
            }
          }
        }
      }

      // 6. Save Button
      Button(
        onClick = {
          val parsedAmount = amountStr.toDoubleOrNull() ?: 0.0
          viewModel.saveCapturedReceipt(
            bitmap = capturedBitmap,
            storeName = storeName.ifBlank { "Receipt" },
            amount = parsedAmount,
            currency = currency,
            category = category,
            purchaseDate = purchaseDateMs,
            rawText = rawOcrText,
            notes = notes.ifBlank { null },
            isWarranty = isWarranty,
            warrantyExpirationDate = if (isWarranty) warrantyExpirationDateMs else null,
            isSubscription = isSubscription,
            subscriptionCycle = if (isSubscription) subscriptionCycle else null,
            subscriptionNextRenewalDate = if (isSubscription) nextRenewalDateMs else null,
            reminderDays = reminderDays,
            onSuccess = { savedId ->
              onSaved(savedId)
            }
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_vault_item_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(10.dp)
      ) {
        Icon(Icons.Default.Check, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save to Vault Ledger", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
