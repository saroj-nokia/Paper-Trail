package com.example.ui.screens.capture

import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.security.ImageFileManager
import com.example.ui.theme.LocalFrostedGlassEnabled
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.PaperTrailMotion
import com.example.ui.theme.frostedGlassBottomBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.math.roundToInt

@Composable
fun InAppCameraScreen(
  onImageCaptured: (Uri) -> Unit,
  onError: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mainExecutor: Executor = remember(context) { ContextCompat.getMainExecutor(context) }
  val frostedGlassEnabled = LocalFrostedGlassEnabled.current
  val hazeState = LocalHazeState.current
  val scope = rememberCoroutineScope()

  var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
  var camera by remember { mutableStateOf<Camera?>(null) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
  var previewViewInstance by remember { mutableStateOf<PreviewView?>(null) }

  var isCapturing by remember { mutableStateOf(false) }
  var initializationError by remember { mutableStateOf<String?>(null) }

  // Available cameras tracking
  var availableCameraInfos by remember { mutableStateOf<List<CameraInfo>>(emptyList()) }
  var currentLensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

  // Zoom management
  var currentZoomRatio by remember { mutableFloatStateOf(1f) }
  var minZoomRatio by remember { mutableFloatStateOf(1f) }
  var maxZoomRatio by remember { mutableFloatStateOf(1f) }

  // Tap-to-focus indicator
  var focusPoint by remember { mutableStateOf<Offset?>(null) }

  BackHandler {
    onDismiss()
  }

  fun initialErrorHandling(message: String) {
    initializationError = message
    onError(message)
  }

  // Bind or re-bind camera when lens facing changes
  fun bindCameraUseCases(provider: ProcessCameraProvider, lensFacing: Int) {
    try {
      provider.unbindAll()

      val preview = Preview.Builder().build()
      val capture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

      val selector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

      // Confirm selector has a matching camera, fallback to default if not
      val actualSelector = if (provider.hasCamera(selector)) {
        selector
      } else {
        CameraSelector.DEFAULT_BACK_CAMERA
      }

      val boundCamera = provider.bindToLifecycle(
        lifecycleOwner,
        actualSelector,
        preview,
        capture
      )

      previewViewInstance?.let { pv ->
        preview.setSurfaceProvider(pv.surfaceProvider)
      }

      camera = boundCamera
      imageCapture = capture

      // Observe zoom state
      boundCamera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
        zoomState?.let {
          currentZoomRatio = it.zoomRatio
          minZoomRatio = it.minZoomRatio
          maxZoomRatio = it.maxZoomRatio
        }
      }
    } catch (e: Exception) {
      initialErrorHandling(e.message ?: "Failed to initialize camera sensor")
    }
  }

  // Initialize CameraProvider on entry
  LaunchedEffect(Unit) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
      try {
        val provider = cameraProviderFuture.get()
        cameraProvider = provider
        val infos = provider.availableCameraInfos
        availableCameraInfos = infos

        // Prefer BACK camera if available, otherwise first available
        val initialFacing = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
          CameraSelector.LENS_FACING_BACK
        } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
          CameraSelector.LENS_FACING_FRONT
        } else {
          CameraSelector.LENS_FACING_BACK
        }
        currentLensFacing = initialFacing
        bindCameraUseCases(provider, initialFacing)
      } catch (e: Exception) {
        initialErrorHandling(e.message ?: "Could not access camera provider")
      }
    }, mainExecutor)
  }

  // Cleanup on exit
  DisposableEffect(Unit) {
    onDispose {
      cameraProvider?.unbindAll()
    }
  }

  // Shutter action
  fun takePhoto() {
    val capture = imageCapture
    if (capture == null || isCapturing) return

    val targetUri = try {
      ImageFileManager.createTempImageUri(context)
    } catch (e: Exception) {
      onError("Failed to allocate image file: ${e.message}")
      return
    }

    // Resolve target File from Uri
    val targetFile = File(context.cacheDir, "receipts_cache/${targetUri.lastPathSegment}")
    targetFile.parentFile?.mkdirs()

    val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()

    isCapturing = true
    capture.takePicture(
      outputOptions,
      mainExecutor,
      object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
          isCapturing = false
          onImageCaptured(targetUri)
        }

        override fun onError(exception: ImageCaptureException) {
          isCapturing = false
          onError("Photo capture failed: ${exception.message}")
        }
      }
    )
  }

  // Tap-to-focus trigger
  fun triggerTapToFocus(offset: Offset, pv: PreviewView) {
    val cam = camera ?: return
    focusPoint = offset
    scope.launch {
      delay(1200)
      if (focusPoint == offset) {
        focusPoint = null
      }
    }

    val meteringPointFactory = pv.meteringPointFactory
    val point = meteringPointFactory.createPoint(offset.x, offset.y)
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
      .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
      .build()
    cam.cameraControl.startFocusAndMetering(action)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("in_app_camera_overlay")
  ) {
    // 1. Camera Preview Viewport
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          scaleType = PreviewView.ScaleType.FILL_CENTER
          setBackgroundColor(AndroidColor.BLACK)
          previewViewInstance = this

          // Setup pinch to zoom on PreviewView native gestures
          val scaleGestureDetector = ScaleGestureDetector(
            ctx,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
              override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val currentZoom = currentZoomRatio
                val delta = detector.scaleFactor
                val targetZoom = (currentZoom * delta).coerceIn(minZoomRatio, maxZoomRatio)
                cam.cameraControl.setZoomRatio(targetZoom)
                return true
              }
            }
          )

          setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
          }
        }
      },
      update = { pv ->
        previewViewInstance = pv
      },
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            previewViewInstance?.let { pv ->
              triggerTapToFocus(offset, pv)
            }
          }
        }
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              if (event.type == PointerEventType.Scroll) {
                val cam = camera
                if (cam != null) {
                  val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                  if (scrollDelta != 0f) {
                    val step = if (scrollDelta < 0) 1.15f else 0.85f
                    val newZoom = (currentZoomRatio * step).coerceIn(minZoomRatio, maxZoomRatio)
                    cam.cameraControl.setZoomRatio(newZoom)
                  }
                }
              }
            }
          }
        }
    )

    // Tap-to-focus ring indicator
    focusPoint?.let { pt ->
      val ringScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = PaperTrailMotion.expressiveExpand(),
        label = "focus_ring_scale"
      )
      Box(
        modifier = Modifier
          .offset { IntOffset(pt.x.roundToInt() - 36, pt.y.roundToInt() - 36) }
          .size(72.dp)
          .scale(ringScale)
          .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
      )
    }

    // 2. Top Bar Controls
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onDismiss,
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.5f))
          .testTag("in_app_camera_close_button")
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = "Close Built-in Camera",
          tint = Color.White
        )
      }

      Text(
        text = "Receipt Scanner",
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        fontWeight = FontWeight.Bold
      )

      // Camera Flip Button (Only shown if more than 1 camera exists)
      if (availableCameraInfos.size > 1) {
        IconButton(
          onClick = {
            val provider = cameraProvider ?: return@IconButton
            val nextFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
              CameraSelector.LENS_FACING_FRONT
            } else {
              CameraSelector.LENS_FACING_BACK
            }
            currentLensFacing = nextFacing
            bindCameraUseCases(provider, nextFacing)
          },
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("in_app_camera_switch_button")
        ) {
          Icon(
            imageVector = Icons.Default.Cameraswitch,
            contentDescription = "Switch Camera Lens",
            tint = Color.White
          )
        }
      } else {
        Spacer(modifier = Modifier.size(44.dp))
      }
    }

    // 3. Bottom Controls Area (Zoom chips + Shutter)
    val controlBarModifier = if (frostedGlassEnabled) {
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .frostedGlassBottomBar(hazeState, enabled = true)
        .padding(bottom = 24.dp, top = 12.dp, start = 16.dp, end = 16.dp)
    } else {
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
        .padding(bottom = 24.dp, top = 12.dp, start = 16.dp, end = 16.dp)
    }

    Column(
      modifier = controlBarModifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Zoom selector chips (derived dynamically from camera min/max zoom ratio capabilities)
      val supportedZoomRatios = remember(minZoomRatio, maxZoomRatio) {
        val candidates = listOf(0.5f, 1.0f, 2.0f, 3.0f, 5.0f)
        candidates.filter { it in minZoomRatio..maxZoomRatio }
      }

      if (supportedZoomRatios.size > 1) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          supportedZoomRatios.forEach { ratio ->
            val isSelected = (currentZoomRatio - ratio).let { Math.abs(it) < 0.2f }
            val chipBg by animateColorAsState(
              targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
              label = "zoom_chip_bg"
            )

            Box(
              modifier = Modifier
                .clip(CircleShape)
                .background(chipBg)
                .clickable {
                  camera?.cameraControl?.setZoomRatio(ratio)
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (ratio == 0.5f) ".5x" else "${ratio.toInt()}x",
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // Shutter Button Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val shutterScale by animateFloatAsState(
          targetValue = if (isPressed) PaperTrailMotion.PRESS_SCALE_DOWN else 1f,
          animationSpec = PaperTrailMotion.pressScaleSpec(),
          label = "shutter_scale"
        )

        Box(
          modifier = Modifier
            .size(76.dp)
            .scale(shutterScale)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
              interactionSource = interactionSource,
              indication = androidx.compose.foundation.LocalIndication.current,
              enabled = !isCapturing
            ) {
              takePhoto()
            }
            .testTag("in_app_camera_shutter_button"),
          contentAlignment = Alignment.Center
        ) {
          if (isCapturing) {
            CircularProgressIndicator(
              modifier = Modifier.size(32.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 3.dp
            )
          } else {
            Icon(
              imageVector = Icons.Default.PhotoCamera,
              contentDescription = "Capture Receipt Photo",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(32.dp)
            )
          }
        }
      }
    }

    // Error State overlay
    if (initializationError != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.85f))
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
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Camera Unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = initializationError ?: "Hardware error",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}
