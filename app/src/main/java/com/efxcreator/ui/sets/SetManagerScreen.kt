package com.efxcreator.ui.sets

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.efxcreator.viewmodel.SetsViewModel

/** 편집 중인 세트 (isNew 이면 "추가" 제목) */
private data class Editing<T>(val value: T, val isNew: Boolean)

/**
 * 이펙트 세트 / 팔레트 세트 관리 화면.
 * 내장 세트는 읽기 전용이고, "복제"해서 내 세트로 만든 뒤 수정할 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: SetsViewModel = viewModel()
) {
    val effectSets by viewModel.effectSets.collectAsState()
    val paletteSets by viewModel.paletteSets.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var effectEditing by remember { mutableStateOf<Editing<EffectRuleSet>?>(null) }
    var paletteEditing by remember { mutableStateOf<Editing<PaletteSet>?>(null) }
    var deleteEffect by remember { mutableStateOf<EffectRuleSet?>(null) }
    var deletePalette by remember { mutableStateOf<PaletteSet?>(null) }

    fun newEffectSet() = EffectRuleSet(viewModel.newEffectSetId(), "", SetCatalog.DEFAULT_RULES)
    fun newPaletteSet() = PaletteSet(
        viewModel.newPaletteSetId(), "",
        RgbColor(255, 0, 155), RgbColor(164, 0, 255), RgbColor(0, 116, 255), RgbColor(0, 255, 255)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("세트 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (tab == 0) effectEditing = Editing(newEffectSet(), true)
                else paletteEditing = Editing(newPaletteSet(), true)
            }) {
                Icon(Icons.Default.Add, contentDescription = "세트 추가")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("이펙트 세트") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("팔레트 세트") })
            }

            if (tab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                            onEdit = { effectEditing = Editing(set, false) },
                            onCopy = {
                                effectEditing = Editing(
                                    set.copy(id = viewModel.newEffectSetId(), name = "${set.name} 복사본", builtIn = false),
                                    true
                                )
                            },
                            onDelete = { deleteEffect = set }
                        ) {
                            Text(
                                set.summary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
                            onEdit = { paletteEditing = Editing(set, false) },
                            onCopy = {
                                paletteEditing = Editing(
                                    set.copy(id = viewModel.newPaletteSetId(), name = "${set.name} 복사본", builtIn = false),
                                    true
                                )
                            },
                            onDelete = { deletePalette = set }
                        ) {
                            PaletteStrip(set.colors, swatchSize = 32)
                        }
                    }
                }
            }
        }
    }

    effectEditing?.let { e ->
        EffectSetEditorDialog(
            initial = e.value,
            isNew = e.isNew,
            onSave = {
                viewModel.saveEffectSet(it)
                effectEditing = null
            },
            onDismiss = { effectEditing = null }
        )
    }
    paletteEditing?.let { e ->
        PaletteSetEditorDialog(
            initial = e.value,
            isNew = e.isNew,
            onSave = {
                viewModel.savePaletteSet(it)
                paletteEditing = null
            },
            onDismiss = { paletteEditing = null }
        )
    }
    deleteEffect?.let { set ->
        ConfirmDelete(set.name, onConfirm = {
            viewModel.deleteEffectSet(set.id)
            deleteEffect = null
        }, onDismiss = { deleteEffect = null })
    }
    deletePalette?.let { set ->
        ConfirmDelete(set.name, onConfirm = {
            viewModel.deletePaletteSet(set.id)
            deletePalette = null
        }, onDismiss = { deletePalette = null })
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
