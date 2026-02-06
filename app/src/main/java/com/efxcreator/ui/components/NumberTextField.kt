package com.efxcreator.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType

/**
 * ✅ 재사용 가능한 숫자 입력 TextField
 *
 * ## 특징
 * - 포커스 시 "0" 자동 지우기
 * - 포커스 해제 시 빈 값이면 "0" 복원
 * - 숫자 키보드 자동 표시
 * - 범위 제한 (range)
 *
 * ## 사용 예시
 * ```kotlin
 * NumberTextField(
 *     value = minutes,
 *     onValueChange = { minutes = it },
 *     label = "min",
 *     modifier = Modifier.weight(1f)
 * )
 *
 * NumberTextField(
 *     value = seconds,
 *     onValueChange = { seconds = it },
 *     label = "sec",
 *     range = 0..59,
 *     modifier = Modifier.weight(1f)
 * )
 * ```
 *
 * @param value 현재 값
 * @param onValueChange 값 변경 콜백
 * @param label TextField 레이블
 * @param range 허용 범위 (기본: 0..Int.MAX_VALUE)
 * @param modifier Modifier
 */
@Composable
fun NumberTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    range: IntRange = 0..Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    // 내부적으로 보여줄 텍스트 상태
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    // 포커스를 잃었을 때 복구하기 위해 "마지막으로 유효했던 값"을 기억합니다.
    var lastValidValue by remember { mutableStateOf(value) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newValue ->
            // 숫자만 입력 가능하도록 필터링 (선택 사항)
            if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                textValue = newValue

                // 실시간으로 유효한 숫자인 경우에만 부모 상태를 업데이트
                newValue.toIntOrNull()?.let { intValue ->
                    if (intValue in range) {
                        lastValidValue = intValue // 유효하므로 백업
                        onValueChange(intValue)
                    }
                }
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                // ✅ 1. 포커스 시 무조건 빈 문자열
                textValue = ""
            } else {
                // ✅ 2. 포커스 해제 시 빈 값이면 원래 있던(마지막으로 유효했던) 값으로 복원
                if (textValue.isEmpty()) {
                    textValue = lastValidValue.toString()
                    onValueChange(lastValidValue)
                } else {
                    // 숫자가 범위를 벗어난 상태로 포커스가 나갔을 경우를 대비해 보정
                    val finalValue = textValue.toIntOrNull()?.coerceIn(range) ?: lastValidValue
                    textValue = finalValue.toString()
                    onValueChange(finalValue)
                }
            }
        }
    )
}