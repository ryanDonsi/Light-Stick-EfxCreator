package com.efxcreator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 어두운 색상표.
 *
 * 임의로 고른 색이 아니라 Material Color Utilities 의 TonalSpot 알고리즘으로 시드 색 #1976D2 에서
 * 만든 표준 톤 팔레트다 (Material Theme Builder 와 같은 방식). 화면 코드는 색을 직접 쓰지 않고
 * `MaterialTheme.colorScheme.*` 역할(primary, surfaceContainer, onSurfaceVariant …)만 사용한다.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB2C8EC),
    onPrimary = Color(0xFF2C415F),
    primaryContainer = Color(0xFF3E5373),
    onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF4B6080),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF364151),
    secondaryContainer = Color(0xFF323C4C),
    onSecondaryContainer = Color(0xFFB5C0D4),
    tertiary = Color(0xFFE6DAFF),
    onTertiary = Color(0xFF554A73),
    tertiaryContainer = Color(0xFFD9CAFB),
    onTertiaryContainer = Color(0xFF4C4169),
    background = Color(0xFF0C0E12),
    onBackground = Color(0xFFE2E5EF),
    surface = Color(0xFF0C0E12),
    onSurface = Color(0xFFE2E5EF),
    surfaceVariant = Color(0xFF22262D),
    onSurfaceVariant = Color(0xFFA7ABB4),
    surfaceTint = Color(0xFFB2C8EC),
    inverseSurface = Color(0xFFF9F9FE),
    inverseOnSurface = Color(0xFF535559),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    outline = Color(0xFF72757E),
    outlineVariant = Color(0xFF444850),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF282C34),
    surfaceDim = Color(0xFF0C0E12),
    surfaceContainer = Color(0xFF171A1F),
    surfaceContainerHigh = Color(0xFF1C2026),
    surfaceContainerHighest = Color(0xFF22262D),
    surfaceContainerLow = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF000000)
)

/**
 * Material Design 3 셰이프 스케일. 화면마다 임의의 둥글기를 쓰지 않고 이 단계 중에서 고른다.
 * extraSmall 색 견본·작은 칩 / small 타일·입력 / medium 카드 / large 패널 / extraLarge 다이얼로그
 */
val EfxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EfxCreatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = EfxShapes,
        content = content
    )
}
