package com.efxcreator.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.model.EfxProjectMetadata
import com.efxcreator.ui.sets.SetSelectionDialog
import com.efxcreator.viewmodel.EfxListViewModel
import com.efxcreator.viewmodel.SetsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EfxListScreen(
    onNavigateToEdit: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSets: () -> Unit = {},
    viewModel: EfxListViewModel = viewModel(),
    setsViewModel: SetsViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val autoGenState by viewModel.autoGenState.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    // 음악을 고른 뒤, 생성 전에 이펙트/팔레트 세트를 고르는 단계
    var pendingMusicUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val effectSets by setsViewModel.effectSets.collectAsState()
    val paletteSets by setsViewModel.paletteSets.collectAsState()

    val autoMusicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) pendingMusicUri = uri
    }

    // 자동 생성 완료 → 편집 화면 이동 / 실패 → 안내
    LaunchedEffect(autoGenState) {
        when (val state = autoGenState) {
            is EfxListViewModel.AutoGenState.Done -> {
                viewModel.consumeAutoGenState()
                onNavigateToEdit(state.projectId)
            }
            is EfxListViewModel.AutoGenState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.consumeAutoGenState()
            }
            else -> Unit
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("새 EFX 만들기") },
            text = { Text("빈 프로젝트에서 직접 편집하거나, 음악을 분석해 이펙트를 자동으로 생성할 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    autoMusicPicker.launch("audio/*")
                }) {
                    Text("음악으로 자동 생성")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    val newProjectId = viewModel.createNewProject()
                    onNavigateToEdit(newProjectId)
                }) {
                    Text("빈 프로젝트")
                }
            }
        )
    }

    pendingMusicUri?.let { uri ->
        SetSelectionDialog(
            title = "생성할 이펙트 스타일",
            confirmLabel = "생성",
            effectSets = effectSets,
            paletteSets = paletteSets,
            initialEffectId = setsViewModel.lastEffectSetId,
            initialPaletteId = setsViewModel.lastPaletteSetId,
            onConfirm = { effectId, paletteId ->
                val options = setsViewModel.rememberSelection(effectId, paletteId)
                pendingMusicUri = null
                viewModel.createProjectFromMusic(uri, options)
            },
            onManageSets = {
                pendingMusicUri = null
                onNavigateToSets()
            },
            onDismiss = { pendingMusicUri = null }
        )
    }

    (autoGenState as? EfxListViewModel.AutoGenState.Generating)?.let { generating ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("이펙트 자동 생성 중") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(generating.musicName, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "비트·섹션·장르를 분석하고 있습니다. 곡 길이에 따라 수십 초가 걸릴 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelAutoGeneration() }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EFX 목록") },
                actions = {
                    IconButton(onClick = onNavigateToSets) {
                        Icon(Icons.Default.Tune, contentDescription = "이펙트·팔레트 세트")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "새로 만들기")
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EFX 프로젝트가 없습니다.\n+ 버튼을 눌러 생성하세요!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { project ->
                    EfxProjectCard(
                        project = project,
                        entryCount = viewModel.getEntryCount(project.id),
                        musicId = viewModel.getMusicId(project.id),
                        onEdit = { onNavigateToEdit(project.id) },
                        onSave = { projectId, projectName ->
                            viewModel.prepareExportFile(projectId, projectName)
                        },
                        onShare = { projectId, projectName ->
                            viewModel.prepareExportFile(projectId, projectName)
                        },
                        onFileSaved = { sourceFile, destinationUri ->
                            viewModel.saveExportedFile(sourceFile, destinationUri)
                        },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun EfxProjectCard(
    project: EfxProjectMetadata,
    entryCount: Int,
    musicId: Int,
    onEdit: () -> Unit,
    onSave: (String, String) -> File,
    onShare: (String, String) -> File,
    onFileSaved: (File, android.net.Uri) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var tempFileToSave by remember { mutableStateOf<File?>(null) }

    // 파일 저장 위치 선택 런처
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            tempFileToSave?.let { sourceFile ->
                onFileSaved(sourceFile, it)
                Toast.makeText(
                    context,
                    "파일이 저장되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                tempFileToSave = null
            }
        }
    }

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
                    .clickable(onClick = onEdit)
                    .padding(16.dp)
                    .padding(top = 8.dp, bottom = 8.dp)  // 위아래 여유 공간
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (musicId != 0) {
                        Text(
                            text = "Music ID: 0x${musicId.toUInt().toString(16).uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "음악 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "타임라인: ${entryCount}개 엔트리",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 우측 상단 X 버튼
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 우측 하단 3점 메뉴
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "메뉴",
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // 저장하기
                    DropdownMenuItem(
                        text = { Text("저장하기") },
                        onClick = {
                            val file = onSave(project.id, project.name)
                            tempFileToSave = file
                            saveFileLauncher.launch("${project.name}.efx")
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )

                    // 공유
                    DropdownMenuItem(
                        text = { Text("공유") },
                        onClick = {
                            val file = onShare(project.id, project.name)
                            shareEfxFile(context, file)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    )
                }
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("프로젝트 삭제") },
            text = { Text("정말로 \"${project.name}\"을(를) 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

/**
 * EFX 파일 공유
 */
private fun shareEfxFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "EFX 공유")
        )
    } catch (e: Exception) {
        android.util.Log.e("EfxListScreen", "Error sharing file", e)
    }
}