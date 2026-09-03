package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.PaperTrailMotion
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.VaultItem
import com.example.ui.theme.MonospaceAmountStyle
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerforatedReceiptCard(
  item: VaultItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
  val dateStr = dateFormat.format(Date(item.purchaseDate))
  val currencyFmt = remember { NumberFormat.getCurrencyInstance(Locale.US) }
  val amountStr = currencyFmt.format(item.amount)

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
    animationSpec = PaperTrailMotion.pressScaleSpec(),
    label = "press_scale"
  )

  Card(
    modifier = modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(RoundedCornerShape(12.dp))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = androidx.compose.foundation.LocalIndication.current,
        onClick = onClick
      )
      .testTag("vault_item_card_${item.id}"),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(animationSpec = PaperTrailMotion.expressiveExpand())
    ) {
      // Top Sawtooth / Perforated strip header
      ReceiptPerforatedHeader()

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Thumbnail preview or Receipt icon
        if (!item.imagePath.isNullOrEmpty() && File(item.imagePath).exists()) {
          AsyncImage(
            model = File(item.imagePath),
            contentDescription = "Receipt photo thumbnail for ${item.storeName}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(54.dp)
              .clip(RoundedCornerShape(8.dp))
              .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
          )
        } else {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.ReceiptLong,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.storeName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
          )

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = dateStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Price amount
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = amountStr,
            style = MonospaceAmountStyle,
            color = MaterialTheme.colorScheme.primary
          )

          if (item.isSubscription && item.cycleEnum != null) {
            Text(
              text = "/${item.cycleEnum!!.label.lowercase()}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.size(20.dp)
        )
      }

      // Dashed divider line
      DashedDivider(modifier = Modifier.padding(horizontal = 16.dp))

      // Badges footer row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        CategoryBadge(category = item.category)

        if (item.isWarranty) {
          WarrantyBadge(item = item)
        }

        if (item.isSubscription) {
          SubscriptionBadge(item = item)
        }
      }
    }
  }
}

@Composable
fun ReceiptPerforatedHeader(modifier: Modifier = Modifier) {
  val strokeColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
  val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(6.dp)
  ) {
    val toothWidth = 14f
    val toothCount = (size.width / toothWidth).toInt()

    val path = Path().apply {
      moveTo(0f, 0f)
      for (i in 0 until toothCount) {
        val startX = i * toothWidth
        val midX = startX + (toothWidth / 2f)
        val endX = (i + 1) * toothWidth
        lineTo(midX, size.height)
        lineTo(endX, 0f)
      }
      lineTo(size.width, 0f)
    }

    drawPath(
      path = path,
      color = dotColor.copy(alpha = 0.15f)
    )
  }
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) {
  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(1.dp)
  ) {
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    drawLine(
      color = color,
      start = Offset(0f, 0f),
      end = Offset(size.width, 0f),
      strokeWidth = 2f,
      pathEffect = pathEffect
    )
  }
}
