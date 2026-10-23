package com.efxcreator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 앱 전체 어두운 테마 — 에디터 화면의 색(배경 0xFF101014, 패널 0xFF1B1B21)과 맞춘다. */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0B2A4A),
    primaryContainer = Color(0xFF1F4E79),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFCE93D8),
    tertiary = Color(0xFFA5D6A7),
    background = Color(0xFF101014),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1B1B21),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2A2A32),
    onSurfaceVariant = Color(0xFFC9C5CA),
    outline = Color(0xFF8F8B94),
    outlineVariant = Color(0xFF45444B),
    surfaceContainerLowest = Color(0xFF0C0C10),
    surfaceContainerLow = Color(0xFF16161B),
    surfaceContainer = Color(0xFF1B1B21),
    surfaceContainerHigh = Color(0xFF22222A),
    surfaceContainerHighest = Color(0xFF2A2A32)
)

@Composable
fun EfxCreatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
