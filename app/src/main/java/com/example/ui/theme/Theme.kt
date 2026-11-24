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

private val SaraikiDarkColorScheme = darkColorScheme(
    primary = SaraikiCyan,
    onPrimary = Color(0xFF071520),
    primaryContainer = SaraikiSurfaceHighDark,
    onPrimaryContainer = SaraikiTextWhite,
    secondary = SaraikiSand,
    onSecondary = Color(0xFF241B0E),
    background = SaraikiBgDark,
    onBackground = SaraikiTextWhite,
    surface = SaraikiSurfaceDark,
    onSurface = SaraikiTextWhite,
    surfaceVariant = SaraikiSurfaceHighDark,
    onSurfaceVariant = SaraikiHintSlate,
    outline = Color(0xFF4A5568)
)

private val SaraikiLightColorScheme = lightColorScheme(
    primary = SaraikiCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F2FD),
    onPrimaryContainer = Color(0xFF082D42),
    secondary = Color(0xFF8C6239),
    onSecondary = Color.White,
    background = DaylightBg,
    onBackground = DaylightText,
    surface = DaylightSurface,
    onSurface = DaylightText,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightHint,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun SaraikiProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> SaraikiDarkColorScheme
        else -> SaraikiLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SaraikiTypography,
        content = content
    )
}
