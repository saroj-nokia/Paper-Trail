package com.example.securevault.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFrostedGlassEnabled
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.frostedGlassBottomBar
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.securevault.logging.CryptoLogEntry
import com.example.securevault.logging.CryptoLogLevel
import com.example.securevault.logging.CryptoLogger

private val TerminalDarkBg = Color(0xFF0F172A)
private val TerminalHeaderBg = Color(0xFF1E293B)
private val TerminalGreen = Color(0xFF10B981)
private val TerminalAmber = Color(0xFFF59E0B)
private val TerminalCyan = Color(0xFF06B6D4)
private val TerminalRed = Color(0xFFEF4444)
private val TerminalTextMuted = Color(0xFF94A3B8)
private val TerminalText = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoTerminalBottomSheet(
  onDismiss: () -> Unit
) {
  val logs by CryptoLogger.logs.collectAsStateWithLifecycle()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
  val frostedGlassEnabled = LocalFrostedGlassEnabled.current
  val hazeState = LocalHazeState.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = if (frostedGlassEnabled) Color.Transparent else TerminalDarkBg,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    modifier = Modifier
      .testTag("crypto_terminal_sheet")
      .then(
        if (frostedGlassEnabled) {
          Modifier
            .background(
              color = TerminalDarkBg.copy(alpha = 0.92f),
              shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .drawWithContent {
              drawContent()
              val strokePx = 1.dp.toPx()
              drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, strokePx / 2),
                end = Offset(size.width, strokePx / 2),
                strokeWidth = strokePx
              )
            }
        } else {
          Modifier
        }
      )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(520.dp)
    ) {
      // Terminal Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(TerminalHeaderBg)
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(TerminalCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = null,
              tint = TerminalCyan,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "CRYPTO SECURITY TERMINAL",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = Color.White,
              letterSpacing = 0.5.sp
            )
            Text(
              text = "Live Hardware Keystore & AES-256 Logs",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = TerminalTextMuted
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(
            onClick = { CryptoLogger.clearLogs() },
            modifier = Modifier.testTag("terminal_clear_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Clear logs",
              tint = TerminalTextMuted,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Clear",
              color = TerminalTextMuted,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("terminal_close_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close Terminal",
              tint = Color.White
            )
          }
        }
      }

      // Terminal Log Area
      if (logs.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = null,
              tint = TerminalTextMuted.copy(alpha = 0.4f),
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "No cryptographic events logged yet.",
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = TerminalTextMuted
            )
            Text(
              text = "Import, export, or preview a file to watch real-time encryption telemetry.",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = TerminalTextMuted.copy(alpha = 0.7f),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(logs, key = { it.id }) { logEntry ->
            TerminalLogItem(logEntry)
          }
        }
      }
    }
  }
}

@Composable
private fun TerminalLogItem(entry: CryptoLogEntry) {
  val (badgeColor, badgeBg) = when (entry.level) {
    CryptoLogLevel.HARDWARE -> TerminalCyan to TerminalCyan.copy(alpha = 0.15f)
    CryptoLogLevel.SUCCESS -> TerminalGreen to TerminalGreen.copy(alpha = 0.15f)
    CryptoLogLevel.WARNING -> TerminalAmber to TerminalAmber.copy(alpha = 0.15f)
    CryptoLogLevel.ERROR -> TerminalRed to TerminalRed.copy(alpha = 0.15f)
    CryptoLogLevel.INFO -> Color(0xFF60A5FA) to Color(0xFF60A5FA).copy(alpha = 0.15f)
  }

  Surface(
    color = Color(0xFF1E293B).copy(alpha = 0.7f),
    shape = RoundedCornerShape(8.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.Top
    ) {
      // Timestamp
      Text(
        text = entry.formattedTime,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = TerminalTextMuted,
        modifier = Modifier.padding(top = 2.dp)
      )

      Spacer(modifier = Modifier.width(8.dp))

      // Tag Badge
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .background(badgeBg)
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          text = entry.tag,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 9.sp,
          color = badgeColor
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Message
      Text(
        text = entry.message,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TerminalText,
        lineHeight = 15.sp,
        modifier = Modifier.weight(1f)
      )
    }
  }
}
