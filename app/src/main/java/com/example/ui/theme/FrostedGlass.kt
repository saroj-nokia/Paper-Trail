package com.example.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

val LocalFrostedGlassEnabled = compositionLocalOf { false }
val LocalSetFrostedGlassEnabled = staticCompositionLocalOf<(Boolean) -> Unit> { {} }
val LocalHazeState = compositionLocalOf<HazeState?> { null }
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

// Default dark theme glass tuning matching the app's palette
val GlassTintAlpha = 0.72f
val GlassBlurRadius = 20.dp
val GlassHighlightColor = Color.White.copy(alpha = 0.08f)

@Composable
fun frostedGlassStyle(
  tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = GlassTintAlpha),
  blurRadius: Dp = GlassBlurRadius
): HazeStyle {
  return HazeStyle(
    blurRadius = blurRadius,
    tints = listOf(HazeTint(tintColor))
  )
}

/**
 * Applies Haze blur effect to top app bars floating over scrolling content.
 * Draws a subtle 1dp bottom highlight border catching the light edge.
 */
@Composable
fun Modifier.frostedGlassTopBar(
  hazeState: HazeState?,
  enabled: Boolean = LocalFrostedGlassEnabled.current,
  tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = GlassTintAlpha),
  blurRadius: Dp = GlassBlurRadius
): Modifier {
  if (!enabled || hazeState == null) return this

  val style = HazeStyle(blurRadius = blurRadius, tints = listOf(HazeTint(tintColor)))
  return this
    .hazeEffect(state = hazeState, style = style)
    .drawWithContent {
      drawContent()
      val strokePx = 1.dp.toPx()
      drawLine(
        color = GlassHighlightColor,
        start = Offset(0f, size.height - strokePx / 2),
        end = Offset(size.width, size.height - strokePx / 2),
        strokeWidth = strokePx
      )
    }
}

/**
 * Applies Haze blur effect to bottom navigation bars and bottom sheets.
 * Draws a subtle 1dp top highlight border catching the light edge.
 */
@Composable
fun Modifier.frostedGlassBottomBar(
  hazeState: HazeState?,
  enabled: Boolean = LocalFrostedGlassEnabled.current,
  tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = GlassTintAlpha),
  blurRadius: Dp = GlassBlurRadius
): Modifier {
  if (!enabled || hazeState == null) return this

  val style = HazeStyle(blurRadius = blurRadius, tints = listOf(HazeTint(tintColor)))
  return this
    .hazeEffect(state = hazeState, style = style)
    .drawWithContent {
      drawContent()
      val strokePx = 1.dp.toPx()
      drawLine(
        color = GlassHighlightColor,
        start = Offset(0f, strokePx / 2),
        end = Offset(size.width, strokePx / 2),
        strokeWidth = strokePx
      )
    }
}

/**
 * Applies Haze blur effect and subtle border to floating dialogs and modal surfaces.
 */
@Composable
fun Modifier.frostedGlassDialog(
  hazeState: HazeState?,
  shape: Shape = RoundedCornerShape(20.dp),
  enabled: Boolean = LocalFrostedGlassEnabled.current,
  tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = GlassTintAlpha),
  blurRadius: Dp = GlassBlurRadius
): Modifier {
  if (!enabled || hazeState == null) return this

  val style = HazeStyle(blurRadius = blurRadius, tints = listOf(HazeTint(tintColor)))
  return this
    .hazeEffect(state = hazeState, style = style)
    .border(1.dp, GlassHighlightColor, shape)
}

/**
 * Marks scrolling background content as a Haze source if frosted glass is enabled.
 */
fun Modifier.frostedGlassSource(
  hazeState: HazeState?,
  enabled: Boolean
): Modifier {
  if (!enabled || hazeState == null) return this
  return this.hazeSource(state = hazeState)
}
