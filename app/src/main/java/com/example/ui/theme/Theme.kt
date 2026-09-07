package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = MintLedgerLight,
  onPrimary = CharcoalDark,
  primaryContainer = MintLedgerContainer,
  onPrimaryContainer = ForestContainer,
  secondary = MintLedger,
  onSecondary = CharcoalDark,
  secondaryContainer = CharcoalSurfaceVariant,
  onSecondaryContainer = WarmParchmentBg,
  tertiary = AmberAlert,
  onTertiary = CharcoalDark,
  tertiaryContainer = AmberAlertOnContainer,
  onTertiaryContainer = AmberAlertContainer,
  background = CharcoalDark,
  onBackground = WarmParchmentBg,
  surface = CharcoalSurface,
  onSurface = WarmParchmentBg,
  surfaceVariant = CharcoalSurfaceVariant,
  onSurfaceVariant = WarmParchmentCard,
  outline = InkLight
)

private val LightColorScheme = lightColorScheme(
  primary = ForestPrimary,
  onPrimary = ForestOnPrimary,
  primaryContainer = ForestContainer,
  onPrimaryContainer = ForestOnContainer,
  secondary = MintLedgerContainer,
  onSecondary = ForestOnPrimary,
  secondaryContainer = WarmParchmentCard,
  onSecondaryContainer = InkBlack,
  tertiary = AmberAlert,
  onTertiary = ForestOnPrimary,
  tertiaryContainer = AmberAlertContainer,
  onTertiaryContainer = AmberAlertOnContainer,
  background = WarmParchmentBg,
  onBackground = InkBlack,
  surface = WarmParchmentSurface,
  onSurface = InkBlack,
  surfaceVariant = WarmParchmentCard,
  onSurfaceVariant = InkMuted,
  outline = WarmParchmentBorder
)

@Composable
fun PaperTrailTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) = PaperTrailTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
