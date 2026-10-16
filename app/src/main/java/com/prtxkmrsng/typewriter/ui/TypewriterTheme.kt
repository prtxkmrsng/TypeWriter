package com.prtxkmrsng.typewriter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VintagePaper = Color(0xFFF4ECD8)
val VintageInk = Color(0xFF2C241E)
val VintageAccent = Color(0xFF8B4513)
val DarkBackground = Color(0xFF1E1B18)
val DarkPaper = Color(0xFF2B2622)
val DarkInk = Color(0xFFE8DFD8)

private val LightColorScheme = lightColorScheme(
    primary = VintageAccent,
    background = VintagePaper,
    surface = VintagePaper,
    onPrimary = Color.White,
    onBackground = VintageInk,
    onSurface = VintageInk
)

private val DarkColorScheme = darkColorScheme(
    primary = VintageAccent,
    background = DarkBackground,
    surface = DarkPaper,
    onPrimary = Color.White,
    onBackground = DarkInk,
    onSurface = DarkInk
)

val TypewriterTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    )
)

@Composable
fun TypewriterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TypewriterTypography,
        content = content
    )
}
