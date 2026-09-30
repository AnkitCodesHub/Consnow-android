package com.Consnow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Teal400 = Color(0xFF26A69A)
private val Teal600 = Color(0xFF00897B)
private val Teal800 = Color(0xFF00695C)
private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate800 = Color(0xFF1E293B)
private val Slate900 = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    secondary = Slate800,
    background = Slate50,
    surface = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    error = Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Color.Black,
    primaryContainer = Teal800,
    secondary = Color(0xFFCBD5E1),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    error = Color(0xFFEF5350),
)

val ConsnowTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.W300, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W600),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W500),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.W600),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W400, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W500, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W500, letterSpacing = 0.5.sp),
)

@Composable
fun ConsnowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ConsnowTypography,
        content = content
    )
}
