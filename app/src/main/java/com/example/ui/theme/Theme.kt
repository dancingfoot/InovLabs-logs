package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = InovCyanDark,
    onPrimary = InovSlateDarkBackground,
    primaryContainer = InovCyanDarker,
    onPrimaryContainer = InovCyanLight,
    secondary = InovGreenDark,
    onSecondary = InovSlateDarkBackground,
    secondaryContainer = InovGreenDarker,
    onSecondaryContainer = InovGreenLight,
    tertiary = InovAmberWarningDark,
    background = InovSlateDarkBackground,
    surface = InovSlateDarkSurface,
    onSurface = InovSlateLightSurface,
    surfaceVariant = InovSlateDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFCA5A5)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = InovCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = InovCyanLight,
    onPrimaryContainer = InovCyanDarker,
    secondary = InovGreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = InovGreenLight,
    onSecondaryContainer = InovGreenDarker,
    tertiary = InovAmberWarning,
    background = InovSlateLightBackground,
    surface = InovSlateLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = InovSlateLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Brand colors requested from logo
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
