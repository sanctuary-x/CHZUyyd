package com.chzu.yyd

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0B2B5B)
val Blue = Color(0xFF1E88E5)
val Teal = Color(0xFF00A6B8)
val Bg = Color(0xFFF4F7FC)
val CardBg = Color(0xFFFFFFFF)
val Muted = Color(0xFF6B7A90)
val OkColor = Color(0xFF1B9E5A)
val WarnColor = Color(0xFFE08A00)
val ErrColor = Color(0xFFD64545)

private val colors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Navy,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F5F8),
    onSecondaryContainer = Color(0xFF064A52),
    background = Bg,
    onBackground = Color(0xFF1B2533),
    surface = CardBg,
    onSurface = Color(0xFF1B2533),
    surfaceVariant = Color(0xFFEDF2F9),
    onSurfaceVariant = Color(0xFF47566B),
    error = ErrColor,
    onError = Color.White,
)

@Composable
fun CHZUTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
