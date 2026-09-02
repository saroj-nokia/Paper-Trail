package com.example.securevault.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.EnhancedEncryption
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.securevault.model.SecureFileItem
import com.example.ui.theme.PaperTrailMotion
import kotlinx.coroutines.launch
import java.util.Locale

private val SecureVaultAmber = Color(0xFFD97706)
private val DarkViewerBackground = Color(0xFF090B0E)

@Composable
fun SecureImageViewerScreen(
  item: SecureFileItem,
  decryptedBytes: ByteArray?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val isGif = remember(item.mimeType, item.originalFileName) {
    item.mimeType.equals("image/gif", ignoreCase = true) ||
        item.originalFileName.endsWith(".gif", ignoreCase = true)
  }

  var decodedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isDecoding by remember { mutableStateOf(!isGif) }
  var decodeError by remember { mutableStateOf(false) }

  // Decode non-GIF images in background
  LaunchedEffect(decryptedBytes, isGif) {
    if (!isGif) {
      if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
        isDecoding = true
        decodeError = false
        try {
          // Read image bounds first to compute safe inSampleSize without full memory allocation
          val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
          }
          BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, boundsOptions)
          val rawW = boundsOptions.outWidth
          val rawH = boundsOptions.outHeight

          // Ensure bitmap dimensions don't exceed OpenGL / Android Canvas max limits (max 4096px / 64MB)
          val maxDimension = 4096
          var sampleSize = 1
          while (
            (rawW > 0 && (rawW / sampleSize) > maxDimension) ||
            (rawH > 0 && (rawH / sampleSize) > maxDimension) ||
            ((rawW.toLong() * rawH.toLong() * 4L) / (sampleSize.toLong() * sampleSize.toLong()) > 48L * 1024L * 1024L)
          ) {
            sampleSize *= 2
          }

          val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
          }
          val bitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, decodeOptions)
          if (bitmap != null) {
            decodedBitmap = bitmap
          } else {
            decodeError = true
          }
        } catch (e: Throwable) {
          decodeError = true
        } finally {
          isDecoding = false
        }
      } else {
        decodeError = true
        isDecoding = false
      }
    }
  }

  // Strictly ensure volatile decrypted content is recycled on navigation away
  DisposableEffect(Unit) {
    onDispose {
      if (decodedBitmap?.isRecycled == false) {
        try {
          decodedBitmap?.recycle()
        } catch (e: Throwable) {
          // ignore
        }
      }
      decodedBitmap = null
    }
  }

  // Animation values for smooth zoom and pan
  val scaleAnim = remember { Animatable(1f) }
  val offsetXAnim = remember { Animatable(0f) }
  val offsetYAnim = remember { Animatable(0f) }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("secure_image_viewer_screen"),
    color = DarkViewerBackground
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Main Pinch / Pan / Double-tap Interactive Viewport
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
          .clipToBounds(),
        contentAlignment = Alignment.Center
      ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        Box(
          modifier = Modifier
            .fillMaxSize()
            .pointerInput(containerWidth, containerHeight) {
              detectTapGestures(
                onDoubleTap = { tapOffset ->
                  coroutineScope.launch {
                    if (scaleAnim.value > 1.15f) {
                      // Reset to 1x fit-to-screen
                      launch {
                        scaleAnim.animateTo(
                          targetValue = 1f,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                          )
                        )
                      }
                      launch {
                        offsetXAnim.animateTo(
                          targetValue = 0f,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                          )
                        )
                      }
                      launch {
                        offsetYAnim.animateTo(
                          targetValue = 0f,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                          )
                        )
                      }
                    } else {
                      // Zoom to 2x centered on tap location
                      val targetScale = 2.0f
                      val center = Offset(containerWidth / 2f, containerHeight / 2f)
                      val targetOffset = (center - tapOffset) * (targetScale - 1f)
                      val maxX = (containerWidth * (targetScale - 1f)) / 2f
                      val maxY = (containerHeight * (targetScale - 1f)) / 2f
                      val clampedX = targetOffset.x.coerceIn(-maxX, maxX)
                      val clampedY = targetOffset.y.coerceIn(-maxY, maxY)

                      launch {
                        scaleAnim.animateTo(
                          targetValue = targetScale,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                          )
                        )
                      }
                      launch {
                        offsetXAnim.animateTo(
                          targetValue = clampedX,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                          )
                        )
                      }
                      launch {
                        offsetYAnim.animateTo(
                          targetValue = clampedY,
                          animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                          )
                        )
                      }
                    }
                  }
                }
              )
            }
            .pointerInput(containerWidth, containerHeight) {
              detectTransformGestures { _, pan, zoom, _ ->
                val currentScale = scaleAnim.value
                val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                val maxX = ((containerWidth * newScale - containerWidth) / 2f).coerceAtLeast(0f)
                val maxY = ((containerHeight * newScale - containerHeight) / 2f).coerceAtLeast(0f)

                val newOffsetX = if (newScale <= 1f) 0f else (offsetXAnim.value + pan.x).coerceIn(-maxX, maxX)
                val newOffsetY = if (newScale <= 1f) 0f else (offsetYAnim.value + pan.y).coerceIn(-maxY, maxY)

                coroutineScope.launch {
                  scaleAnim.snapTo(newScale)
                  offsetXAnim.snapTo(newOffsetX)
                  offsetYAnim.snapTo(newOffsetY)
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          when {
            isDecoding -> {
              CircularProgressIndicator(
                color = SecureVaultAmber,
                modifier = Modifier.size(48.dp)
              )
            }
            decodeError -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.BrokenImage,
                  contentDescription = null,
                  tint = SecureVaultAmber,
                  modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Unable to Decode Image",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "The decrypted byte payload could not be rendered as a valid image format.",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.7f),
                  textAlign = TextAlign.Center
                )
              }
            }
            isGif -> {
              val imageLoader = remember(context) {
                ImageLoader.Builder(context)
                  .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                      add(ImageDecoderDecoder.Factory())
                    } else {
                      add(GifDecoder.Factory())
                    }
                  }
                  .build()
              }
              val imageRequest = remember(decryptedBytes) {
                ImageRequest.Builder(context)
                  .data(decryptedBytes)
                  .build()
              }

              AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = item.originalFileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                  }
                  .testTag("secure_image_viewer_image")
              )
            }
            decodedBitmap != null -> {
              Image(
                bitmap = decodedBitmap!!.asImageBitmap(),
                contentDescription = item.originalFileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                  }
                  .testTag("secure_image_viewer_image")
              )
            }
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
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val isBackPressed by backInteractionSource.collectIsPressedAsState()
            val backScale by animateFloatAsState(
              targetValue = if (isBackPressed) PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
              animationSpec = PaperTrailMotion.pressScaleSpec(),
              label = "back_press_scale"
            )

            IconButton(
              onClick = onBack,
              interactionSource = backInteractionSource,
              modifier = Modifier
                .graphicsLayer {
                  scaleX = backScale
                  scaleY = backScale
                }
                .testTag("secure_image_viewer_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to SecureVault",
                tint = Color.White
              )
            }

            Spacer(modifier = Modifier.width(4.dp))

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
                  text = "${formatFileSize(item.fileSizeBytes)} • Decrypted in RAM",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  color = SecureVaultAmber,
                  fontSize = 11.sp
                )
              }
            }
          }

          // Zoom Reset indicator / action when zoomed in
          AnimatedVisibility(
            visible = scaleAnim.value > 1.05f,
            enter = fadeIn(PaperTrailMotion.fadeIn),
            exit = fadeOut(PaperTrailMotion.fadeOut)
          ) {
            Surface(
              onClick = {
                coroutineScope.launch {
                  launch { scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                  launch { offsetXAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                  launch { offsetYAnim.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                }
              },
              shape = RoundedCornerShape(16.dp),
              color = SecureVaultAmber.copy(alpha = 0.25f),
              modifier = Modifier.padding(end = 8.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.ZoomOutMap,
                  contentDescription = "Reset Zoom",
                  tint = SecureVaultAmber,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = String.format(Locale.US, "%.1fx", scaleAnim.value),
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  fontWeight = FontWeight.Bold,
                  color = SecureVaultAmber
                )
              }
            }
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
