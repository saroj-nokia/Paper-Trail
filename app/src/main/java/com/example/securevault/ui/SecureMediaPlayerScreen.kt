package com.example.securevault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.securevault.data.SecureVaultRepository
import com.example.securevault.media.SecureVaultDecryptingDataSource
import com.example.securevault.model.SecureFileItem
import com.example.ui.theme.LocalForceHideBottomBar
import com.example.ui.theme.PaperTrailMotion
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

private val SecureVaultAmber = Color(0xFFD97706)
private val DarkPlayerBackground = Color(0xFF090B0E)
private val CardBackground = Color(0xFF13171E)

@OptIn(UnstableApi::class)
@Composable
fun SecureMediaPlayerScreen(
  item: SecureFileItem,
  authorizedCipher: Cipher,
  repository: SecureVaultRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val isVideo = remember(item.mimeType) { item.mimeType.startsWith("video/") }

  var isFullscreen by remember { mutableStateOf(false) }

  fun setFullscreen(enable: Boolean) {
    isFullscreen = enable
    val act = context.findActivity() ?: return
    val window = act.window ?: return
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (enable) {
      act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      insetsController.hide(WindowInsetsCompat.Type.systemBars())
      insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
      act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  // Back-press: exit fullscreen first if active, otherwise navigate back
  BackHandler {
    if (isFullscreen) {
      setFullscreen(false)
    } else {
      onBack()
    }
  }

  // Force-hide bottom navigation bar during the entire lifetime of media playback
  val forceHideBottomBar = LocalForceHideBottomBar.current
  DisposableEffect(Unit) {
    forceHideBottomBar.value = true
    onDispose {
      forceHideBottomBar.value = false
    }
  }

  // Ensure orientation and system bars are unconditionally restored on exit
  DisposableEffect(Unit) {
    onDispose {
      val act = context.findActivity()
      if (act != null) {
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        act.window?.let { window ->
          WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }

  var isPlaying by remember { mutableStateOf(false) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var isBuffering by remember { mutableStateOf(true) }
  var playbackError by remember { mutableStateOf<String?>(null) }
  var isDraggingSlider by remember { mutableStateOf(false) }
  var dragPositionMs by remember { mutableLongStateOf(0L) }

  // Build Media3 ExoPlayer instance with custom SecureVaultDecryptingDataSource
  val exoPlayer = remember {
    val dataSourceFactory = SecureVaultDecryptingDataSource.Factory(
      repository = repository,
      item = item,
      authorizedCipher = authorizedCipher
    )
    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
      .createMediaSource(MediaItem.fromUri(Uri.parse("securevault://${item.id}/${item.originalFileName}")))

    ExoPlayer.Builder(context).build().apply {
      setMediaSource(mediaSource)
      prepare()
      playWhenReady = true
    }
  }

  // Setup Player Listeners and release on disposal
  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        isBuffering = playbackState == Player.STATE_BUFFERING
        if (playbackState == Player.STATE_READY) {
          durationMs = exoPlayer.duration.coerceAtLeast(0L)
        }
      }

      override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
      }

      override fun onPlayerError(error: PlaybackException) {
        playbackError = error.message ?: "Failed to play encrypted media stream"
      }
    }

    exoPlayer.addListener(listener)

    onDispose {
      exoPlayer.removeListener(listener)
      exoPlayer.stop()
      exoPlayer.release()
    }
  }

  // Coroutine to poll current playback position
  LaunchedEffect(isPlaying, isDraggingSlider) {
    while (true) {
      if (!isDraggingSlider && exoPlayer.playbackState != Player.STATE_IDLE) {
        currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (exoPlayer.duration > 0) {
          durationMs = exoPlayer.duration
        }
      }
      delay(250)
    }
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("secure_media_player_screen"),
    color = DarkPlayerBackground
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      if (isVideo) {
        // Video Player Viewport
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          AndroidView(
            factory = { ctx ->
              PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 3500
                layoutParams = FrameLayout.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
              }
            },
            modifier = Modifier
              .fillMaxSize()
              .testTag("secure_video_player_view")
          )

          if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
              color = SecureVaultAmber,
              modifier = Modifier.size(52.dp)
            )
          }

          if (playbackError != null) {
            PlaybackErrorOverlay(
              error = playbackError!!,
              onRetry = {
                playbackError = null
                exoPlayer.prepare()
                exoPlayer.play()
              }
            )
          }
        }
      } else {
        // Audio Player Viewport
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .statusBarsPadding()
            .padding(top = 64.dp, bottom = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          // Animated Audio Artwork / Visualizer Card
          AudioVisualizerCard(
            isPlaying = isPlaying,
            fileName = item.originalFileName,
            mimeType = item.mimeType
          )

          Spacer(modifier = Modifier.height(32.dp))

          // Track Information
          Text(
            text = item.originalFileName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = Icons.Default.EnhancedEncryption,
              contentDescription = null,
              tint = SecureVaultAmber,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "AES-256 Decrypted Stream • ${formatFileSize(item.fileSizeBytes)}",
              style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
              color = SecureVaultAmber
            )
          }

          Spacer(modifier = Modifier.height(32.dp))

          if (playbackError != null) {
            PlaybackErrorOverlay(
              error = playbackError!!,
              onRetry = {
                playbackError = null
                exoPlayer.prepare()
                exoPlayer.play()
              }
            )
          } else {
            // Seek Bar & Timers
            val displayPosition = if (isDraggingSlider) dragPositionMs else currentPositionMs
            val progressFraction = if (durationMs > 0) {
              (displayPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Slider(
              value = progressFraction,
              onValueChange = { frac ->
                isDraggingSlider = true
                dragPositionMs = (frac * durationMs).toLong()
              },
              onValueChangeFinished = {
                exoPlayer.seekTo(dragPositionMs)
                isDraggingSlider = false
              },
              colors = SliderDefaults.colors(
                thumbColor = SecureVaultAmber,
                activeTrackColor = SecureVaultAmber,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("media_seek_bar")
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = formatDuration(displayPosition),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White.copy(alpha = 0.7f)
              )
              Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White.copy(alpha = 0.7f)
              )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Audio Playback Controls
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Replay 10s
              IconButton(
                onClick = {
                  val newPos = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                  exoPlayer.seekTo(newPos)
                },
                modifier = Modifier.size(52.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Replay10,
                  contentDescription = "Replay 10 seconds",
                  tint = Color.White,
                  modifier = Modifier.size(32.dp)
                )
              }

              Spacer(modifier = Modifier.width(20.dp))

              // Play / Pause FAB
              val playInteractionSource = remember { MutableInteractionSource() }
              val isPlayPressed by playInteractionSource.collectIsPressedAsState()
              val playScale by animateFloatAsState(
                targetValue = if (isPlayPressed) PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
                animationSpec = PaperTrailMotion.pressScaleSpec(),
                label = "play_press_scale"
              )

              Surface(
                onClick = {
                  if (isPlaying) {
                    exoPlayer.pause()
                  } else {
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                      exoPlayer.seekTo(0)
                    }
                    exoPlayer.play()
                  }
                },
                shape = CircleShape,
                color = SecureVaultAmber,
                interactionSource = playInteractionSource,
                modifier = Modifier
                  .size(72.dp)
                  .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                  }
                  .testTag("media_play_pause_button")
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.fillMaxSize()
                ) {
                  if (isBuffering) {
                    CircularProgressIndicator(
                      color = Color.Black,
                      modifier = Modifier.size(28.dp)
                    )
                  } else {
                    Icon(
                      imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                      contentDescription = if (isPlaying) "Pause" else "Play",
                      tint = Color.Black,
                      modifier = Modifier.size(38.dp)
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.width(20.dp))

              // Forward 10s
              IconButton(
                onClick = {
                  val maxPos = if (durationMs > 0) durationMs else Long.MAX_VALUE
                  val newPos = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxPos)
                  exoPlayer.seekTo(newPos)
                },
                modifier = Modifier.size(52.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Forward10,
                  contentDescription = "Forward 10 seconds",
                  tint = Color.White,
                  modifier = Modifier.size(32.dp)
                )
              }
            }
          }
        }
      }

      // Top App Bar Overlay / Controls
      if (!isFullscreen) {
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
              label = "player_back_press_scale"
            )

            IconButton(
              onClick = onBack,
              interactionSource = backInteractionSource,
              modifier = Modifier
                .graphicsLayer {
                  scaleX = backScale
                  scaleY = backScale
                }
                .testTag("media_player_back_button")
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
                  text = "${if (isVideo) "Encrypted Video" else "Encrypted Audio"} • Stream Decryption",
                  style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                  color = SecureVaultAmber,
                  fontSize = 11.sp
                )
              }
            }

            if (isVideo) {
              IconButton(
                onClick = { setFullscreen(true) },
                modifier = Modifier.testTag("video_fullscreen_toggle_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Fullscreen,
                  contentDescription = "Enter Fullscreen",
                  tint = Color.White
                )
              }
            }
          }
        }
      } else if (isVideo) {
        // Exit Fullscreen Floating Button (top-right)
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          contentAlignment = Alignment.TopEnd
        ) {
          Surface(
            onClick = { setFullscreen(false) },
            shape = CircleShape,
            color = Color(0x99000000),
            shadowElevation = 4.dp,
            modifier = Modifier
              .size(44.dp)
              .testTag("video_exit_fullscreen_button")
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.fillMaxSize()
            ) {
              Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "Exit Fullscreen",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AudioVisualizerCard(
  isPlaying: Boolean,
  fileName: String,
  mimeType: String
) {
  val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
  val bar1 by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar1"
  )
  val bar2 by infiniteTransition.animateFloat(
    initialValue = 0.85f,
    targetValue = 0.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(550, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar2"
  )
  val bar3 by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(350, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar3"
  )
  val bar4 by infiniteTransition.animateFloat(
    initialValue = 0.9f,
    targetValue = 0.2f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar4"
  )
  val bar5 by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 0.8f,
    animationSpec = infiniteRepeatable(
      animation = tween(450, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bar5"
  )

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = CardBackground,
    shadowElevation = 8.dp,
    modifier = Modifier
      .size(240.dp)
      .clip(RoundedCornerShape(24.dp))
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(
              SecureVaultAmber.copy(alpha = if (isPlaying) 0.22f else 0.08f),
              Color.Transparent
            )
          )
        )
    ) {
      if (isPlaying) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.height(100.dp)
        ) {
          val bars = listOf(bar1, bar2, bar3, bar4, bar5)
          bars.forEach { barScale ->
            Box(
              modifier = Modifier
                .width(10.dp)
                .height((100 * barScale).dp)
                .clip(CircleShape)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(SecureVaultAmber, SecureVaultAmber.copy(alpha = 0.4f))
                  )
                )
            )
          }
        }
      } else {
        Icon(
          imageVector = Icons.Default.GraphicEq,
          contentDescription = null,
          tint = SecureVaultAmber.copy(alpha = 0.8f),
          modifier = Modifier.size(96.dp)
        )
      }
    }
  }
}

@Composable
private fun PlaybackErrorOverlay(
  error: String,
  onRetry: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier.padding(32.dp)
  ) {
    Icon(
      imageVector = Icons.Default.ErrorOutline,
      contentDescription = null,
      tint = SecureVaultAmber,
      modifier = Modifier.size(56.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = "Media Playback Error",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = error,
      style = MaterialTheme.typography.bodySmall,
      color = Color.White.copy(alpha = 0.7f),
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
      onClick = onRetry,
      shape = RoundedCornerShape(12.dp),
      color = SecureVaultAmber
    ) {
      Text(
        text = "Retry Playback",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
    }
  }
}

private fun formatDuration(durationMs: Long): String {
  if (durationMs <= 0) return "00:00"
  val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
  val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
  val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
  return if (hours > 0) {
    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.US, "%02d:%02d", minutes, seconds)
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

private fun Context.findActivity(): Activity? {
  var current = this
  while (current is ContextWrapper) {
    if (current is Activity) return current
    current = current.baseContext
  }
  return null
}

