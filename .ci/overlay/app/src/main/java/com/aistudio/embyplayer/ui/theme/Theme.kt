package com.aistudio.embyplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Dark = darkColorScheme(
    primary = Color(0xFF52B54B),
    onPrimary = Color(0xFF081108),
    primaryContainer = Color(0xFF193D1B),
    onPrimaryContainer = Color(0xFFC8F4C5),
    secondary = Color(0xFF9DD79A),
    tertiary = Color(0xFF76D1C7),
    background = Color(0xFF090D10),
    surface = Color(0xFF0F1519),
    surfaceVariant = Color(0xFF192126),
    surfaceContainer = Color(0xFF121A1F),
    surfaceContainerHigh = Color(0xFF1A2329),
    onBackground = Color(0xFFF2F6F4),
    onSurface = Color(0xFFF2F6F4),
    onSurfaceVariant = Color(0xFFB6C2BE),
    outline = Color(0xFF45534F),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun EmbyPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = AppTypography, shapes = AppShapes, content = content)
}