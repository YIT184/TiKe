package com.codex.shuatitimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF7F2E8)
val PaperDeep = Color(0xFFECE3D3)
val Porcelain = Color(0xFFFFFDF8)
val Ink = Color(0xFF18332A)
val Forest = Color(0xFF2B634E)
val Moss = Color(0xFF91A68F)
val Vermilion = Color(0xFFD86A4F)
val Ochre = Color(0xFFC8994D)
val SoftLine = Color(0xFFD8D0C2)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EBDE),
    onPrimaryContainer = Ink,
    secondary = Vermilion,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2D8),
    onSecondaryContainer = Color(0xFF5A1D10),
    tertiary = Ochre,
    background = Paper,
    onBackground = Ink,
    surface = Porcelain,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Color(0xFF526159),
    outline = Color(0xFF78847D),
    outlineVariant = SoftLine
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ACDB1),
    onPrimary = Color(0xFF073827),
    primaryContainer = Color(0xFF1D4E3C),
    onPrimaryContainer = Color(0xFFCBEBD7),
    secondary = Color(0xFFFFB5A2),
    onSecondary = Color(0xFF5D1608),
    secondaryContainer = Color(0xFF7B2E1B),
    onSecondaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF111C18),
    onBackground = Color(0xFFE2E9E3),
    surface = Color(0xFF18251F),
    onSurface = Color(0xFFE2E9E3),
    surfaceVariant = Color(0xFF25342D),
    onSurfaceVariant = Color(0xFFBDC9C1),
    outline = Color(0xFF87958C),
    outlineVariant = Color(0xFF3D4A43)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun ShuatiTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
