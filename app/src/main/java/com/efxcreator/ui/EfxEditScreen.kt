package com.efxcreator.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.viewmodel.EfxEditViewModel
import com.lightstick.efx.EfxEntry
import com.lightstick.types.Color
import com.lightstick.types.EffectType
import com.efxcreator.R
import com.efxcreator.core.Loc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EfxEditScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: EfxEditViewModel = viewModel()
) {
    val metadata by viewModel.metadata.collectAsState()
    val efx by viewModel.efx.collectAsState()
    val editingEntry by viewModel.editingEntry.collectAsState()
    val suggestedName by viewModel.suggestedName.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showMusicDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var deleteTargetIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showPermissionDialog = true
        }
    }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setMusicFile(uri)
        showMusicDialog = false
    }

    fun selectMusic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
        musicPicker.launch("audio/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Loc.str(R.string.efx_edit_screen_1)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Loc.str(R.string.efx_edit_screen_2))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = Loc.str(R.string.efx_edit_screen_3))
            }
        }
    ) { padding ->
        metadata?.let { meta ->
            efx?.let { currentEfx ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 음악 파일명 제안 카드
                    suggestedName?.let { suggested ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.str(R.string.efx_edit_screen_4),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "\"$suggested\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Row {
                                    TextButton(onClick = { viewModel.applySuggestedName() }) {
                                        Text(Loc.str(R.string.efx_edit_screen_5))
                                    }
                                    IconButton(onClick = { viewModel.dismissSuggestion() }) {
                                        Icon(Icons.Default.Close, Loc.str(R.string.efx_edit_screen_6))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 헤더 정보 카드 (패딩 최적화)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // EFX 이름 섹션
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.str(R.string.efx_edit_screen_7),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = meta.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                TextButton(onClick = { showNameDialog = true }) {
                                    Text(Loc.str(R.string.efx_edit_screen_8))
                                }
                            }

                            // 음악 파일 섹션
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = Loc.str(R.string.efx_edit_screen_9),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        text = if (currentEfx.header.musicId != 0) {
                                            "Music ID: 0x${currentEfx.header.musicId.toUInt().toString(16).uppercase()}"
                                        } else {
                                            Loc.str(R.string.efx_edit_screen_10)
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                TextButton(onClick = { showMusicDialog = true }) {
                                    Text(Loc.str(R.string.efx_edit_screen_8))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = Loc.str(R.string.efx_edit_screen_11, currentEfx.body.entries.size),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentEfx.body.entries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = Loc.str(R.string.efx_edit_screen_12),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)  // ✅ FAB 공간 확보
                        ) {
                            itemsIndexed(currentEfx.body.entries) { index, entry ->
                                TimelineEntryCard(
                                    entry = entry,
                                    onClick = {
                                        viewModel.startEditingEntry(index, entry)
                                    },
                                    onDelete = {
                                        deleteTargetIndex = index
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 마지막 Entry의 timestamp 계산
    val lastEntryTimestamp = efx?.body?.entries?.maxOfOrNull { it.timestampMs } ?: 0L

    // Entry 추가 Dialog
    if (showAddDialog) {
        TimelineEntryDialog(
            entry = null,
            lastEntryTimestamp = lastEntryTimestamp,
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                viewModel.addTimelineEntry(entry)
                showAddDialog = false
            }
        )
    }

    // Entry 편집 Dialog
    editingEntry?.let { (index, entry) ->
        TimelineEntryDialog(
            entry = entry,
            lastEntryTimestamp = lastEntryTimestamp,
            onDismiss = { viewModel.cancelEditingEntry() },
            onSave = { updatedEntry ->
                viewModel.updateTimelineEntry(index, updatedEntry)
            },
            onDelete = {
                viewModel.deleteTimelineEntry(index)
            }
        )
    }

    // EFX 이름 편집 Dialog
    if (showNameDialog) {
        EditNameDialog(
            currentName = metadata?.name ?: "",
            onDismiss = { showNameDialog = false },
            onSave = { newName ->
                viewModel.updateProjectName(newName)
                showNameDialog = false
            }
        )
    }

    // 음악 파일 편집 Dialog
    if (showMusicDialog) {
        MusicEditDialog(
            currentMusicId = efx?.header?.musicId ?: 0,
            onDismiss = { showMusicDialog = false },
            onSelectMusic = { selectMusic() },
            onDeleteMusic = {
                viewModel.setMusicFile(null)
                showMusicDialog = false
            }
        )
    }

    // Entry 삭제 확인 다이얼로그
    deleteTargetIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { deleteTargetIndex = null },
            title = { Text(Loc.str(R.string.efx_edit_screen_13)) },
            text = { Text(Loc.str(R.string.efx_edit_screen_14)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTimelineEntry(index)
                        deleteTargetIndex = null
                    }
                ) {
                    Text(Loc.str(R.string.efx_edit_screen_15), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetIndex = null }) {
                    Text(Loc.str(R.string.efx_edit_screen_16))
                }
            }
        )
    }

    // 권한 안내 Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(Loc.str(R.string.efx_edit_screen_17)) },
            text = {
                Text(
                    Loc.str(R.string.efx_edit_screen_18)
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(Loc.str(R.string.efx_edit_screen_19))
                }
            }
        )
    }
}

/**
 * Timeline Entry Card (재설계)
 */
@Composable
fun TimelineEntryCard(
    entry: EfxEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 메인 콘텐츠
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(12.dp)
                    .padding(top = 8.dp),  // X 버튼 공간
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Effect Index (최상단)
                Text(
                    text = "Index: ${entry.payload.effectIndex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                // Effect Type (강조) + Timestamp (mm:ss.ms)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.payload.effectType.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = formatTimestamp(entry.timestampMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Effect Parameters
                when (entry.payload.effectType) {
                    EffectType.ON, EffectType.OFF -> {
                        Text(
                            text = "Transit: ${entry.payload.period}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    EffectType.BLINK, EffectType.STROBE, EffectType.BREATH -> {
                        Text(
                            text = "Period: ${entry.payload.period}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Colors
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // OFF는 FG Color 없음
                    if (entry.payload.effectType != EffectType.OFF) {
                        ColorBox(
                            color = entry.payload.color,
                            label = "FG"
                        )
                    }
                    if (entry.payload.effectType in listOf(EffectType.BLINK, EffectType.STROBE, EffectType.BREATH)) {
                        ColorBox(
                            color = entry.payload.backgroundColor,
                            label = "BG"
                        )
                    }
                }

                // Advanced Parameters
                if (entry.payload.spf != 0 || entry.payload.fade != 0 ||
                    entry.payload.randomColor != 0 || entry.payload.randomDelay != 0) {

                    val advancedParams = mutableListOf<String>()
                    if (entry.payload.spf != 0) advancedParams.add("SPF: ${entry.payload.spf}")
                    if (entry.payload.fade != 0) advancedParams.add("밝기: ${entry.payload.fade}")
                    if (entry.payload.randomColor != 0) advancedParams.add("RandColor")
                    if (entry.payload.randomDelay != 0) advancedParams.add("RandDelay: ${entry.payload.randomDelay}")

                    Text(
                        text = advancedParams.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 우측 상단 X 버튼
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = Loc.str(R.string.efx_edit_screen_15),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Timestamp를 mm:ss.ms 형태로 변환
 */
fun formatTimestamp(timestampMs: Long): String {
    val minutes = (timestampMs / 60000).toInt()
    val seconds = ((timestampMs % 60000) / 1000).toInt()
    val millis = (timestampMs % 1000).toInt()
    return String.format("%02d:%02d.%03d", minutes, seconds, millis)
}

@Composable
fun ColorBox(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall
        )
        Surface(
            modifier = Modifier.size(20.dp),
            color = ComposeColor(color.r, color.g, color.b),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {}
        Text(
            text = "RGB(${color.r},${color.g},${color.b})",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * EFX 이름 편집 Dialog
 */
@Composable
fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Loc.str(R.string.efx_edit_screen_20)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(Loc.str(R.string.efx_edit_screen_7)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text(Loc.str(R.string.efx_edit_screen_21))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.str(R.string.efx_edit_screen_16))
            }
        }
    )
}

/**
 * 음악 파일 편집 Dialog
 */
@Composable
fun MusicEditDialog(
    currentMusicId: Int,
    onDismiss: () -> Unit,
    onSelectMusic: () -> Unit,
    onDeleteMusic: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Loc.str(R.string.efx_edit_screen_22)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentMusicId != 0) {
                    Text(
                        text = Loc.str(R.string.efx_edit_screen_23, currentMusicId.toUInt().toString(16).uppercase()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = Loc.str(R.string.efx_edit_screen_24),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentMusicId != 0) {
                    TextButton(
                        onClick = onDeleteMusic,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(Loc.str(R.string.efx_edit_screen_15))
                    }
                }
                TextButton(onClick = onSelectMusic) {
                    Text(Loc.str(R.string.efx_edit_screen_25))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.str(R.string.efx_edit_screen_16))
            }
        }
    )
}