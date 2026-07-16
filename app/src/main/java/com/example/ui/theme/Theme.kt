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

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryBlueDark,
    secondary = AccentTeal,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    outline = DarkBorder,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentTeal,
    background = LightBg,
    surface = LightSurface,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    outline = LightBorder,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // disabled as requested
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
