package com.example.securevault.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.securevault.model.SecureFileItem
import com.example.securevault.pdf.MemFdPdfSource
import com.example.ui.theme.PaperTrailMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

private val SecureVaultAmber = Color(0xFFD97706)
private val DarkPdfViewerBackground = Color(0xFF0D0F14)
private val PageCardBackground = Color(0xFFFFFFFF)

@Composable
fun SecurePdfViewerScreen(
  item: SecureFileItem,
  decryptedBytes: ByteArray?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val coroutineScope = rememberCoroutineScope()
  var pdfSource by remember { mutableStateOf<MemFdPdfSource?>(null) }
  var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
  var pageCount by remember { mutableStateOf(0) }
  var initError by remember { mutableStateOf<String?>(null) }
  var isInitializing by remember { mutableStateOf(true) }

  // Cache rendered page bitmaps
  val renderedBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
  val renderLock = remember { Any() }

  // Zoom and Pan state
  var scale by remember { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }
  val animScale = remember { Animatable(1f) }
  val animOffsetX = remember { Animatable(0f) }
  val animOffsetY = remember { Animatable(0f) }
  var isAnimatingZoom by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()

  // Track current page for the page indicator
  val currentPage by remember {
    derivedStateOf {
      if (pageCount == 0) 1
      else (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount)
    }
  }

  // Initialize MemFd and PdfRenderer
  LaunchedEffect(decryptedBytes) {
    if (decryptedBytes == null || decryptedBytes.isEmpty()) {
      initError = "No decrypted PDF data available."
      isInitializing = false
      return@LaunchedEffect
    }

    withContext(Dispatchers.Default) {
      try {
        val source = MemFdPdfSource.create(decryptedBytes)
        val renderer = PdfRenderer(source.parcelFileDescriptor)
        pdfSource = source
        pdfRenderer = renderer
        pageCount = renderer.pageCount
        isInitializing = false
      } catch (e: Throwable) {
        initError = e.message ?: "Failed to initialize PDF renderer."
        isInitializing = false
      }
    }
  }

  // Pre-render current page and 1 page ahead/behind as user scrolls
  LaunchedEffect(pdfRenderer, pageCount) {
    val renderer = pdfRenderer ?: return@LaunchedEffect
    snapshotFlow { listState.firstVisibleItemIndex }
      .distinctUntilChanged()
      .collect { firstIndex ->
        val targetIndices = listOf(
          firstIndex,
          firstIndex + 1,
          (firstIndex - 1).coerceAtLeast(0)
        ).filter { it in 0 until pageCount }.distinct()

        withContext(Dispatchers.Default) {
          targetIndices.forEach { pageIdx ->
            if (!renderedBitmaps.containsKey(pageIdx)) {
              synchronized(renderLock) {
                if (!renderedBitmaps.containsKey(pageIdx)) {
                  try {
                    val page = renderer.openPage(pageIdx)
                    // High-resolution bitmap render capped to safe max dimensions (max 3840px / 32MB)
                    val baseW = page.width.toFloat().coerceAtLeast(1f)
                    val baseH = page.height.toFloat().coerceAtLeast(1f)
                    val maxDimension = 3840f
                    val densityMultiplier = (maxDimension / maxOf(baseW, baseH)).coerceIn(0.5f, 2f)
                    val width = (baseW * densityMultiplier).toInt().coerceAtLeast(1)
                    val height = (baseH * densityMultiplier).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderedBitmaps[pageIdx] = bitmap
                  } catch (_: Throwable) {}
                }
              }
            }
          }

          // Clean up far-away pages from memory cache to keep RAM footprint low
          val pagesToEvict = renderedBitmaps.keys.filter { idx ->
            idx < firstIndex - 3 || idx > firstIndex + 4
          }
          pagesToEvict.forEach { idx ->
            renderedBitmaps.remove(idx)?.recycle()
          }
        }
      }
  }

  // Cleanup on Dispose: Close PdfRenderer, SharedMemory, and recycle all Bitmaps
  DisposableEffect(Unit) {
    onDispose {
      synchronized(renderLock) {
        try {
          pdfRenderer?.close()
        } catch (_: Exception) {}
        pdfRenderer = null

        try {
          pdfSource?.close()
        } catch (_: Exception) {}
        pdfSource = null

        renderedBitmaps.values.forEach { bmp ->
          try {
            if (!bmp.isRecycled) bmp.recycle()
          } catch (_: Exception) {}
        }
        renderedBitmaps.clear()
      }
    }
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("secure_pdf_viewer_screen"),
    color = DarkPdfViewerBackground
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val maxW = constraints.maxWidth.toFloat()
      val maxH = constraints.maxHeight.toFloat()

      val currentScale = if (isAnimatingZoom) animScale.value else scale
      val currentOffsetX = if (isAnimatingZoom) animOffsetX.value else offsetX
      val currentOffsetY = if (isAnimatingZoom) animOffsetY.value else offsetY

      val isZoomed = currentScale > 1.05f

      if (isInitializing) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            CircularProgressIndicator(
              color = SecureVaultAmber,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Loading Secure PDF in SharedMemory...",
              style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
              color = Color.White.copy(alpha = 0.8f)
            )
          }
        }
      } else if (initError != null) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = SecureVaultAmber,
              modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Failed to Render PDF",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = initError ?: "Unknown error",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.7f),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
              onClick = onBack,
              shape = RoundedCornerShape(12.dp),
              color = SecureVaultAmber
            ) {
              Text(
                text = "Go Back",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
              )
            }
          }
        }
      } else {
        // PDF Pages Vertical Stream with gesture zooming & panning
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                if (!isAnimatingZoom) {
                  val newScale = (scale * zoom).coerceIn(1f, 4f)
                  scale = newScale

                  val maxPanX = if (scale > 1f) (maxW * (scale - 1f)) / 2f else 0f
                  val maxPanY = if (scale > 1f) (maxH * (scale - 1f)) / 2f else 0f

                  offsetX = (offsetX + pan.x * scale).coerceIn(-maxPanX, maxPanX)
                  offsetY = (offsetY + pan.y * scale).coerceIn(-maxPanY, maxPanY)
                }
              }
            }
            .pointerInput(Unit) {
              detectTapGestures(
                onDoubleTap = { tapOffset ->
                  coroutineScope.launch {
                    isAnimatingZoom = true
                    if (scale > 1.2f) {
                      launch { animScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                      launch { animOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                      launch { animOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                      scale = 1f
                      offsetX = 0f
                      offsetY = 0f
                    } else {
                      val targetScale = 2.2f
                      val targetOffsetX = (maxW / 2f - tapOffset.x) * (targetScale - 1f)
                      val targetOffsetY = (maxH / 2f - tapOffset.y) * (targetScale - 1f)
                      val maxPanX = (maxW * (targetScale - 1f)) / 2f
                      val maxPanY = (maxH * (targetScale - 1f)) / 2f
                      val clampedX = targetOffsetX.coerceIn(-maxPanX, maxPanX)
                      val clampedY = targetOffsetY.coerceIn(-maxPanY, maxPanY)

                      launch { animScale.animateTo(targetScale, spring(stiffness = Spring.StiffnessMediumLow)) }
                      launch { animOffsetX.animateTo(clampedX, spring(stiffness = Spring.StiffnessMediumLow)) }
                      launch { animOffsetY.animateTo(clampedY, spring(stiffness = Spring.StiffnessMediumLow)) }
                      scale = targetScale
                      offsetX = clampedX
                      offsetY = clampedY
                    }
                    isAnimatingZoom = false
                  }
                }
              )
            }
            .graphicsLayer {
              scaleX = currentScale
              scaleY = currentScale
              translationX = currentOffsetX
              translationY = currentOffsetY
            }
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .testTag("secure_pdf_lazy_column"),
            contentPadding = PaddingValues(top = 80.dp, bottom = 96.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            items(pageCount, key = { it }) { pageIndex ->
              PdfPageCard(
                pageIndex = pageIndex,
                bitmap = renderedBitmaps[pageIndex],
                pageCount = pageCount
              )
            }
          }
        }

        // Floating Reset Zoom Button
        AnimatedVisibility(
          visible = isZoomed,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 84.dp)
        ) {
          Surface(
            onClick = {
              coroutineScope.launch {
                isAnimatingZoom = true
                launch { animScale.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                launch { animOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                launch { animOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                scale = 1f
                offsetX = 0f
                offsetY = 0f
                isAnimatingZoom = false
              }
            },
            shape = CircleShape,
            color = SecureVaultAmber,
            shadowElevation = 6.dp,
            modifier = Modifier
              .size(48.dp)
              .testTag("reset_pdf_zoom_button")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.fillMaxSize()
            ) {
              Icon(
                imageVector = Icons.Default.ZoomOutMap,
                contentDescription = "Reset Zoom",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
              )
            }
          }
        }

        // Floating Bottom Page Indicator Pill
        AnimatedVisibility(
          visible = pageCount > 0,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xDD12161F),
            shadowElevation = 8.dp,
            modifier = Modifier.testTag("pdf_page_indicator")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = SecureVaultAmber,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Page $currentPage of $pageCount",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
        }

        // Top App Bar Overlay
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .background(Color(0xCC000000))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val isBackPressed by backInteractionSource.collectIsPressedAsState()
            val backScale by animateFloatAsState(
              targetValue = if (isBackPressed) PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
              animationSpec = PaperTrailMotion.pressScaleSpec(),
              label = "pdf_back_press_scale"
            )

            IconButton(
              onClick = onBack,
              interactionSource = backInteractionSource,
              modifier = Modifier
                .graphicsLayer {
                  scaleX = backScale
                  scaleY = backScale
                }
                .testTag("pdf_viewer_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to SecureVault",
                tint = Color.White
              )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.originalFileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.EnhancedEncryption,
                  contentDescription = null,
                  tint = SecureVaultAmber,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "In-Memory PDF (POSIX memfd) • Zero Disk Trace",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  color = SecureVaultAmber,
                  fontSize = 11.sp
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PdfPageCard(
  pageIndex: Int,
  bitmap: Bitmap?,
  pageCount: Int
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = PageCardBackground,
    shadowElevation = 6.dp,
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .testTag("pdf_page_$pageIndex")
  ) {
    if (bitmap != null && !bitmap.isRecycled) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Page ${pageIndex + 1} of $pageCount",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth()
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(360.dp)
          .background(Color(0xFFF3F4F6)),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          CircularProgressIndicator(
            color = SecureVaultAmber,
            strokeWidth = 2.dp,
            modifier = Modifier.size(32.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Rendering page ${pageIndex + 1}...",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
          )
        }
      }
    }
  }
}
