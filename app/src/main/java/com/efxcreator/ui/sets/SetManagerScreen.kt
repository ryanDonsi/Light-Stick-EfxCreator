package com.efxcreator.ui.sets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.music.sets.EffectRuleSet
import com.efxcreator.music.sets.PaletteSet
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.music.sets.SetCatalog
import com.efxcreator.ui.editor.SlimVerticalScrollBar
import com.efxcreator.ui.activityViewModel
import com.efxcreator.viewmodel.PatternsViewModel
import com.efxcreator.viewmodel.SetsViewModel

/** 편집 중인 세트 (isNew 이면 "추가" 제목) */
private data class Editing<T>(val value: T, val isNew: Boolean)

/** 패널 상단 제목 줄: 제목 + "세트 추가" 버튼 (설정 화면의 다른 패널 제목 줄과 높이를 맞춘다) */
@Composable
private fun SetPanelHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("세트 추가")
        }
    }
}

/**
 * 이펙트 세트 관리 패널 (설정 화면의 오른쪽에 들어간다).
 * 내장 세트는 읽기 전용이고, "복제"해서 내 세트로 만든 뒤 수정할 수 있다.
 */
@Composable
fun EffectSetsPanel(
    viewModel: SetsViewModel = viewModel(),
    modifier: Modifier = Modifier,
    patternsVm: PatternsViewModel = activityViewModel()
) {
    val patterns by patternsVm.patterns.collectAsState()
    val effectSets by viewModel.effectSets.collectAsState()
    var editing by remember { mutableStateOf<Editing<EffectRuleSet>?>(null) }
    var deleting by remember { mutableStateOf<EffectRuleSet?>(null) }
    val listState = rememberLazyListState()

    fun newSet() = EffectRuleSet(viewModel.newEffectSetId(), "", SetCatalog.DEFAULT_RULES)

    Column(modifier = modifier) {
        SetPanelHeader("이펙트 세트") { editing = Editing(newSet(), true) }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    InfoCard(
                        "자동 (기본 규칙)",
                        "세트를 고르지 않으면 곡의 섹션·에너지·장르에 따라 기본 규칙으로 패턴을 고릅니다."
                    )
                }
                items(effectSets, key = { it.id }) { set ->
                    SetCard(
                        title = set.name,
                        builtIn = set.builtIn,
                        onEdit = { editing = Editing(set, false) },
                        onCopy = {
                            editing = Editing(
                                set.copy(id = viewModel.newEffectSetId(), name = "${set.name} 복사본", builtIn = false),
                                true
                            )
                        },
                        onDelete = { deleting = set }
                    ) {
                        Text(
                            set.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            SlimVerticalScrollBar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }

    editing?.let { e ->
        EffectSetEditorDialog(
            initial = e.value,
            isNew = e.isNew,
            patterns = patterns,
            onSave = {
                viewModel.saveEffectSet(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { set ->
        ConfirmDelete(set.name, onConfirm = {
            viewModel.deleteEffectSet(set.id)
            deleting = null
        }, onDismiss = { deleting = null })
    }
}

/** 팔레트 세트 관리 패널 (설정 화면의 오른쪽에 들어간다). */
@Composable
fun PaletteSetsPanel(viewModel: SetsViewModel = viewModel(), modifier: Modifier = Modifier) {
    val paletteSets by viewModel.paletteSets.collectAsState()
    var editing by remember { mutableStateOf<Editing<PaletteSet>?>(null) }
    var deleting by remember { mutableStateOf<PaletteSet?>(null) }
    val listState = rememberLazyListState()

    fun newSet() = PaletteSet(
        viewModel.newPaletteSetId(), "",
        RgbColor(255, 0, 155), RgbColor(164, 0, 255), RgbColor(0, 116, 255), RgbColor(0, 255, 255)
    )

    Column(modifier = modifier) {
        SetPanelHeader("팔레트 세트") { editing = Editing(newSet(), true) }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    InfoCard(
                        "자동 (장르·곡 기반)",
                        "세트를 고르지 않으면 장르에 어울리는 색 계열을 곡마다 자동으로 고릅니다."
                    )
                }
                items(paletteSets, key = { it.id }) { set ->
                    SetCard(
                        title = set.name,
                        builtIn = set.builtIn,
                        onEdit = { editing = Editing(set, false) },
                        onCopy = {
                            editing = Editing(
                                set.copy(id = viewModel.newPaletteSetId(), name = "${set.name} 복사본", builtIn = false),
                                true
                            )
                        },
                        onDelete = { deleting = set }
                    ) {
                        PaletteStrip(set.colors, swatchSize = 32)
                    }
                }
            }
            SlimVerticalScrollBar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }

    editing?.let { e ->
        PaletteSetEditorDialog(
            initial = e.value,
            isNew = e.isNew,
            onSave = {
                viewModel.savePaletteSet(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    deleting?.let { set ->
        ConfirmDelete(set.name, onConfirm = {
            viewModel.deletePaletteSet(set.id)
            deleting = null
        }, onDismiss = { deleting = null })
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetCard(
    title: String,
    builtIn: Boolean,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (builtIn) {
                        Spacer(Modifier.width(8.dp))
                        Text("내장", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                content()
            }
            if (!builtIn) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "수정") }
            }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "복제") }
            if (!builtIn) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "삭제") }
            }
        }
    }
}

@Composable
private fun ConfirmDelete(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("세트 삭제") },
        text = { Text("'$name' 세트를 삭제할까요? 이미 만든 EFX 에는 영향이 없습니다.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("삭제") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
