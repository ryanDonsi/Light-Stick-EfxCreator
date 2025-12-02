package com.efxcreator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFFA5D6A7),
    surface = Color(0xFF1C1B1F),           // 다크모드 카드 배경
    surfaceVariant = Color(0xFF49454F)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    secondary = Color(0xFF9C27B0),
    tertiary = Color(0xFF388E3C),
    surface = Color.White,                  // ← 카드 배경색을 흰색으로
    surfaceVariant = Color(0xFFF5F5F5),    // 보조 배경색
    background = Color(0xFFFAFAFA)         // 전체 배경색
)

@Composable
fun EfxCreatorTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}