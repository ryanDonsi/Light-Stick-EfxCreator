package com.efxcreator.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.efxcreator.core.AppLanguage
import com.efxcreator.core.LocaleHelper

/** 설정 > 언어 설정: 자동(시스템 언어) / 한국어 / English / 日本語 / 简体中文 */
@Composable
fun LanguageSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = remember { LocaleHelper.saved(context) }
    var pending by remember { mutableStateOf<AppLanguage?>(null) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "표시 언어를 고르세요. \"자동\"은 기기의 시스템 언어를 따르며, 지원하지 않는 언어는 영어로 표시됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppLanguage.entries.forEach { lang ->
            val selected = lang == current
            Surface(
                onClick = { if (!selected) pending = lang },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(languageLabel(lang), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    pending?.let { lang ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("언어 변경") },
            text = {
                Text("표시 언어를 ${languageLabel(lang)}(으)로 바꾸면 앱이 다시 시작되고, 연결된 디바이스의 연결이 해제됩니다. 계속할까요?")
            },
            confirmButton = {
                TextButton(onClick = {
                    LocaleHelper.save(context, lang)
                    var c = context
                    while (c is ContextWrapper && c !is Activity) c = c.baseContext
                    (c as? Activity)?.let { LocaleHelper.restartApp(it) }
                    pending = null
                }) { Text("변경") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("취소") } }
        )
    }
}

/** "자동 (시스템 언어: 日本語)" / "한국어" … */
private fun languageLabel(lang: AppLanguage): String =
    if (lang == AppLanguage.AUTO) "자동 (시스템 언어: ${AppLanguage.system().nativeName})" else lang.nativeName
