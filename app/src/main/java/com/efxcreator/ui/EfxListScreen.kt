package com.efxcreator.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.model.EfxProjectMetadata
import com.efxcreator.model.Show
import com.efxcreator.ui.device.DevicePopup
import com.efxcreator.ui.device.DevicePopups
import com.efxcreator.ui.device.DeviceStatusButton
import com.efxcreator.ui.device.activityBleViewModel
import com.efxcreator.ui.device.devicePopupFor
import com.efxcreator.ui.editor.SlimVerticalScrollBar
import com.efxcreator.ui.sets.SetSelectionDialog
import com.efxcreator.viewmodel.BleViewModel
import com.efxcreator.viewmodel.EfxListViewModel
import com.efxcreator.viewmodel.SetsViewModel
import com.efxcreator.viewmodel.ShowViewModel
import java.io.File
import com.efxcreator.R
import com.efxcreator.core.Loc

/** 어느 공연에도 속하지 않은 곡을 모아 보여 주는 가상 공연의 id */
private const val UNASSIGNED_ID = "__unassigned__"

/** 왼쪽 공연 목록의 한 줄과 오른쪽 곡 카드가 같은 높이를 갖도록 하는 기준 높이 */
private val LIST_ITEM_HEIGHT = 96.dp

/** 좌우 패널 상단 제목 줄의 높이 (오른쪽의 공연 이미지 64dp 에 맞춤) */
private val PANEL_HEADER_HEIGHT = 64.dp

/**
 * 초기 화면: 왼쪽에 공연 목록, 오른쪽에 선택한 공연의 곡 목록.
 * 공연을 선택해야 곡을 추가할 수 있고, 새 곡은 선택한 공연에 들어간다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EfxListScreen(
    onNavigateToEdit: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSets: () -> Unit = {},
    viewModel: EfxListViewModel = viewModel(),
    setsViewModel: SetsViewModel = viewModel(),
    showViewModel: ShowViewModel = viewModel(),
    bleVm: BleViewModel = activityBleViewModel()
) {
    val projects by viewModel.projects.collectAsState()
    val shows by showViewModel.shows.collectAsState()
    val autoGenState by viewModel.autoGenState.collectAsState()
    val bleConnected by bleVm.connected.collectAsState()
    var devicePopup by remember { mutableStateOf(DevicePopup.NONE) }
    val context = LocalContext.current

    var selectedShowId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf<Show?>(null) }       // 이름 변경 대상
    var newShowDialog by remember { mutableStateOf(false) }
    var deleteShowTarget by remember { mutableStateOf<Show?>(null) }
    var moveSongTarget by remember { mutableStateOf<EfxProjectMetadata?>(null) }
    // 곡을 추가하는 동안 새 곡이 들어갈 공연 (자동 생성은 시간이 걸려 화면이 바뀔 수 있으므로 따로 기억)
    var addTargetShowId by remember { mutableStateOf<String?>(null) }
    // 음악을 고른 뒤, 생성 전에 이펙트/팔레트 세트를 고르는 단계
    var pendingMusicUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val effectSets by setsViewModel.effectSets.collectAsState()
    val paletteSets by setsViewModel.paletteSets.collectAsState()

    // 지워졌거나 아직 고르지 않은 공연 선택은 해제
    val selectedShow = shows.firstOrNull { it.id == selectedShowId }
    val unassigned = remember(projects, shows) {
        val assigned = shows.flatMap { it.songIds }.toSet()
        projects.filter { it.id !in assigned }
    }
    val inUnassigned = selectedShowId == UNASSIGNED_ID
    val songs: List<EfxProjectMetadata> = when {
        selectedShow != null -> selectedShow.songIds.mapNotNull { id -> projects.firstOrNull { it.id == id } }
        inUnassigned -> unassigned
        else -> emptyList()
    }

    // 설정의 "음악 불러오기 위치"(기본: 내장 저장공간의 Music 폴더)에서 시작하고, 여러 곡을 한 번에 고를 수 있는 음악 선택기
    val autoMusicPicker = rememberLauncherForActivityResult(OpenMusicDocuments()) { uris ->
        if (uris.isNotEmpty()) pendingMusicUris = uris
    }

    // 자동 생성 완료 → 공연에 곡 추가 + 편집 화면 이동 / 실패 → 안내
    LaunchedEffect(autoGenState) {
        when (val state = autoGenState) {
            is EfxListViewModel.AutoGenState.Done -> {
                viewModel.consumeAutoGenState()
                onNavigateToEdit(state.projectId)
            }
            is EfxListViewModel.AutoGenState.DoneBatch -> {
                viewModel.consumeAutoGenState()
                val msg = Loc.str(R.string.efx_list_screen_1, state.projectIds.size) +
                    if (state.failed > 0) Loc.str(R.string.efx_list_screen_2, state.failed) else ""
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
            title = { Text(Loc.str(R.string.efx_list_screen_3)) },
            text = {
                Text(Loc.str(R.string.efx_list_screen_4, selectedShow?.name ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    addTargetShowId = selectedShow?.id
                    autoMusicPicker.launch(MusicFolder.initialUri(context))
                }) {
                    Text(Loc.str(R.string.efx_list_screen_5))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    val newProjectId = viewModel.createNewProject()
                    selectedShow?.let { showViewModel.addSong(it.id, newProjectId) }
                    onNavigateToEdit(newProjectId)
                }) {
                    Text(Loc.str(R.string.efx_list_screen_6))
                }
            }
        )
    }

    if (newShowDialog) {
        ShowEditDialog(
            title = Loc.str(R.string.efx_list_screen_7),
            initial = null,
            confirmLabel = Loc.str(R.string.efx_list_screen_8),
            onConfirm = { name, at, image, _ ->
                selectedShowId = showViewModel.createShow(name, at, image)
                newShowDialog = false
            },
            onDismiss = { newShowDialog = false }
        )
    }

    showNameDialog?.let { target ->
        ShowEditDialog(
            title = Loc.str(R.string.efx_list_screen_9),
            initial = target,
            confirmLabel = Loc.str(R.string.efx_edit_screen_21),
            onConfirm = { name, at, image, remove ->
                showViewModel.updateShow(target.id, name, at, image, remove)
                showNameDialog = null
            },
            onDismiss = { showNameDialog = null }
        )
    }

    deleteShowTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteShowTarget = null },
            title = { Text(Loc.str(R.string.efx_list_screen_10)) },
            text = { Text(Loc.str(R.string.efx_list_screen_11, target.name, target.songIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showViewModel.deleteShow(target.id)
                    if (selectedShowId == target.id) selectedShowId = null
                    deleteShowTarget = null
                }) { Text(Loc.str(R.string.efx_edit_screen_15), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteShowTarget = null }) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
        )
    }

    moveSongTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { moveSongTarget = null },
            title = { Text(Loc.str(R.string.efx_list_screen_12, song.name)) },
            text = {
                if (shows.isEmpty()) {
                    Text(Loc.str(R.string.efx_list_screen_13))
                } else {
                    Column {
                        shows.forEach { show ->
                            ListItem(
                                headlineContent = { Text(show.name) },
                                supportingContent = { Text(Loc.str(R.string.efx_list_screen_14, show.songIds.size)) },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.clickable {
                                    showViewModel.moveSong(song.id, show.id)
                                    moveSongTarget = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveSongTarget = null }) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
        )
    }

    if (pendingMusicUris.isNotEmpty()) {
        val uris = pendingMusicUris
        SetSelectionDialog(
            title = Loc.str(R.string.efx_list_screen_15),
            confirmLabel = Loc.str(R.string.efx_list_screen_16),
            effectSets = effectSets,
            paletteSets = paletteSets,
            initialEffectId = setsViewModel.lastEffectSetId,
            initialPaletteId = setsViewModel.lastPaletteSetId,
            onConfirm = { effectId, paletteId ->
                val options = setsViewModel.rememberSelection(effectId, paletteId)
                pendingMusicUris = emptyList()
                // 곡이 하나 만들어질 때마다 선택해 둔 공연에 바로 추가한다
                val targetShow = addTargetShowId
                viewModel.createProjectsFromMusic(uris, options) { projectId ->
                    targetShow?.let { showViewModel.addSong(it, projectId) }
                }
            },
            onManageSets = {
                pendingMusicUris = emptyList()
                onNavigateToSets()
            },
            onDismiss = { pendingMusicUris = emptyList() }
        )
    }

    (autoGenState as? EfxListViewModel.AutoGenState.Generating)?.let { generating ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(Loc.str(R.string.efx_list_screen_17)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (generating.total > 1) {
                        Text("${generating.index} / ${generating.total}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(generating.musicName, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        Loc.str(R.string.efx_list_screen_18),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelAutoGeneration() }) {
                    Text(Loc.str(R.string.efx_edit_screen_16))
                }
            }
        )
    }

    DevicePopups(bleVm, devicePopup) { devicePopup = DevicePopup.NONE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("GLOWSYNC Editor")
                    }
                },
                actions = {
                    // 디바이스 연결 정보: 미연결이면 연결 팝업, 연결 중이면 관리 팝업
                    DeviceStatusButton(bleVm, onClick = { devicePopup = devicePopupFor(bleConnected) })
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = Loc.str(R.string.efx_list_screen_19))
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 좌측: 공연 목록 ─────────────────────────────
            Surface(
                modifier = Modifier.weight(0.36f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(PANEL_HEADER_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Loc.str(R.string.efx_list_screen_20), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { newShowDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Loc.str(R.string.efx_list_screen_21))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (shows.isEmpty() && unassigned.isEmpty()) {
                        EmptyHint(Loc.str(R.string.efx_list_screen_22))
                    } else {
                        val showListState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = showListState,
                            contentPadding = PaddingValues(end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(shows, key = { it.id }) { show ->
                                ShowRow(
                                    name = show.name,
                                    image = { ShowImage(show.imagePath, size = 64.dp) },
                                    subtitle = listOfNotNull(
                                        show.performanceAt?.let { formatShowDate(it) },
                                        Loc.str(R.string.efx_list_screen_14, show.songIds.count { id -> projects.any { it.id == id } })
                                    ).joinToString(" · "),
                                    selected = show.id == selectedShowId,
                                    onClick = { selectedShowId = show.id },
                                    onRename = { showNameDialog = show },
                                    onDelete = { deleteShowTarget = show },
                                    modifier = Modifier.animateItem()
                                )
                            }
                            if (unassigned.isNotEmpty()) {
                                item(key = UNASSIGNED_ID) {
                                    ShowRow(
                                        name = Loc.str(R.string.efx_list_screen_23),
                                        image = null,
                                        subtitle = Loc.str(R.string.efx_list_screen_14, unassigned.size),
                                        selected = inUnassigned,
                                        onClick = { selectedShowId = UNASSIGNED_ID },
                                        onRename = null,
                                        onDelete = null,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                        SlimVerticalScrollBar(showListState, Modifier.align(Alignment.CenterEnd))
                        }
                    }
                }
            }

            // ── 우측: 선택한 공연의 곡 ───────────────────────
            Surface(
                modifier = Modifier.weight(0.64f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(PANEL_HEADER_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedShow != null) {
                            ShowImage(selectedShow.imagePath, size = 64.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    selectedShow != null -> selectedShow.name
                                    inUnassigned -> Loc.str(R.string.efx_list_screen_23)
                                    else -> Loc.str(R.string.efx_list_screen_24)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedShow != null) {
                                Text(
                                    selectedShow.performanceAt?.let { formatShowDate(it) } ?: Loc.str(R.string.efx_list_screen_25),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // 공연을 선택해야 곡을 추가할 수 있다
                        Button(onClick = { showCreateDialog = true }, enabled = selectedShow != null) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(Loc.str(R.string.efx_list_screen_3))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        selectedShowId == null || (selectedShow == null && !inUnassigned) ->
                            EmptyHint(Loc.str(R.string.efx_list_screen_26))
                        songs.isEmpty() ->
                            EmptyHint(Loc.str(R.string.efx_list_screen_27))
                        else -> {
                        val songListState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = songListState,
                            contentPadding = PaddingValues(end = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(songs, key = { it.id }) { project ->
                                EfxProjectCard(
                                    modifier = Modifier.animateItem(),
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
                                    onDelete = {
                                        viewModel.deleteProject(project.id)
                                        showViewModel.removeSongEverywhere(project.id)
                                    },
                                    onMove = { moveSongTarget = project }
                                )
                            }
                        }
                        SlimVerticalScrollBar(songListState, Modifier.align(Alignment.CenterEnd))
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 공연 목록의 한 줄. 선택되면 secondaryContainer 색으로 강조된다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowRow(
    name: String,
    image: (@Composable () -> Unit)?,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.height(LIST_ITEM_HEIGHT).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (image != null) {
                image()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onRename != null && onDelete != null) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = Loc.str(R.string.efx_list_screen_28))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(Loc.str(R.string.efx_list_screen_29)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.str(R.string.efx_edit_screen_15), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EfxProjectCard(
    modifier: Modifier = Modifier,
    project: EfxProjectMetadata,
    entryCount: Int,
    musicId: Int,
    onEdit: () -> Unit,
    onSave: (String, String) -> File,
    onShare: (String, String) -> File,
    onFileSaved: (File, android.net.Uri) -> Unit,
    onDelete: () -> Unit,
    onMove: (() -> Unit)? = null
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
                    Loc.str(R.string.efx_list_screen_30),
                    Toast.LENGTH_SHORT
                ).show()
                tempFileToSave = null
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 메인 콘텐츠
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LIST_ITEM_HEIGHT)
                    .clickable(onClick = onEdit)
                    .padding(start = 16.dp, end = 56.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 음악 파일의 메타데이터(앨범 아트)
                SongArt(musicUriString = project.musicUriString, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f)
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
                            text = Loc.str(R.string.efx_edit_screen_10),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = Loc.str(R.string.efx_list_screen_31, entryCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 우측 3점 메뉴 (저장/공유/이동/삭제) — 공연 목록의 메뉴와 같은 크기, 세로 가운데
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            ) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = Loc.str(R.string.efx_list_screen_32))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // 저장하기
                    DropdownMenuItem(
                        text = { Text(Loc.str(R.string.efx_list_screen_33)) },
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

                    // 다른 공연으로 이동
                    if (onMove != null) {
                        DropdownMenuItem(
                            text = { Text(Loc.str(R.string.efx_list_screen_34)) },
                            onClick = {
                                showMenu = false
                                onMove()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null)
                            }
                        )
                    }

                    // 공유
                    DropdownMenuItem(
                        text = { Text(Loc.str(R.string.efx_list_screen_35)) },
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

                    // 삭제
                    DropdownMenuItem(
                        text = { Text(Loc.str(R.string.efx_edit_screen_15), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
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
            title = { Text(Loc.str(R.string.efx_list_screen_36)) },
            text = { Text(Loc.str(R.string.efx_list_screen_37, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(Loc.str(R.string.efx_edit_screen_15), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(Loc.str(R.string.efx_edit_screen_16))
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
            Intent.createChooser(shareIntent, Loc.str(R.string.efx_list_screen_38))
        )
    } catch (e: Exception) {
        android.util.Log.e("EfxListScreen", "Error sharing file", e)
    }
}