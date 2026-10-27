@file:OptIn(ExperimentalMaterial3Api::class)

package com.efxcreator.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.music.MusicAnalysis
import com.efxcreator.model.UserEffect
import com.efxcreator.ui.MusicFolder
import com.efxcreator.ui.device.DevicePopup
import com.efxcreator.ui.device.DevicePopups
import com.efxcreator.ui.device.activityBleViewModel
import com.efxcreator.ui.device.devicePopupFor
import com.efxcreator.ui.OpenMusicDocuments
import com.efxcreator.ui.SongArt
import com.efxcreator.ui.TimelineEntryDialog
import com.efxcreator.ui.sets.SetDropdown
import com.efxcreator.ui.theme.EfxMotion
import com.efxcreator.ui.sets.SetSelectionDialog
import com.efxcreator.viewmodel.BleViewModel
import com.efxcreator.viewmodel.EffectLibraryViewModel
import com.efxcreator.viewmodel.EfxEditViewModel
import com.efxcreator.viewmodel.PatternsViewModel
import com.efxcreator.viewmodel.SetsViewModel
import com.efxcreator.model.PatternResolver
import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.roundToInt

private val PREVIEW_ICON_SIZE = 84.dp

/**
 * 태블릿(가로) 전용 에디터.
 *
 * ┌──────────────── 상단 바 (이름 / 음악 / 기기) ────────────────┐
 * │ 타임라인 (파형·섹션·비트·이펙트 블록)        │ Preview (LED)  │
 * │──────────────────────────────────────────────│ 재생 컨트롤    │
 * │ 이펙트 라이브러리 (탭 / 길게 눌러 드래그)    │ 선택 이펙트    │
 * └──────────────────────────────────────────────┴────────────────┘
 */
@Composable
fun EfxTabletEditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSets: () -> Unit = {},
    editVm: EfxEditViewModel = viewModel(),
    bleVm: BleViewModel = activityBleViewModel(),
    setsVm: SetsViewModel = viewModel(),
    patternsVm: PatternsViewModel = viewModel(),
    libVm: EffectLibraryViewModel = viewModel()
) {
    val context = LocalContext.current

    val metadata by editVm.metadata.collectAsState()
    val efx by editVm.efx.collectAsState()
    val editingEntry by editVm.editingEntry.collectAsState()
    val analysis by editVm.analysis.collectAsState()
    val analyzing by editVm.analyzing.collectAsState()
    val musicFile by editVm.musicFile.collectAsState()
    val editMessage by editVm.message.collectAsState()
    val applying by editVm.applying.collectAsState()
    val canUndo by editVm.canUndo.collectAsState()
    val canRedo by editVm.canRedo.collectAsState()
    val dirty by editVm.dirty.collectAsState()
    val saving by editVm.saving.collectAsState()
    val userSlots by libVm.slots.collectAsState()
    val effectSets by setsVm.effectSets.collectAsState()
    val paletteSets by setsVm.paletteSets.collectAsState()

    val bleConnected by bleVm.connected.collectAsState()
    val patterns by patternsVm.patterns.collectAsState()

    val entries: List<EfxEntry> = efx?.body?.entries.orEmpty()
    // 실기기로 보내는 프레임: Broadcasting Mode 설정에 따라 신호 전파 여부(broadcasting)를 덮어쓴다
    val broadcasting by bleVm.broadcasting.collectAsState()
    val frames = remember(entries, broadcasting) {
        entries.map { it.timestampMs to it.payload.copy(broadcasting = if (broadcasting) 1 else 0).toByteArray() }
    }

    // ── 재생 / 타임라인 상태 ──────────────────────────────────
    val player = remember { MusicPlayerController() }
    val timeline = remember { TimelineState() }
    val drag = remember { PresetDragState() }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    var pendingSelectTs by remember { mutableStateOf<Long?>(null) }
    var snapToBeats by remember { mutableStateOf(true) }
    var showBeats by remember { mutableStateOf(true) }
    var followPlayhead by remember { mutableStateOf(true) }
    var timelineBounds by remember { mutableStateOf(Rect.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    var showNameDialog by remember { mutableStateOf(false) }
    var devicePopup by remember { mutableStateOf(DevicePopup.NONE) }
    var showApplyDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    // 내 라이브러리에서 추가/수정 중인 칸 번호 (null = 닫힘)
    var libraryEditorSlot by remember { mutableStateOf<Int?>(null) }
    var deviceLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { editVm.loadProject(projectId) }
    LaunchedEffect(musicFile) { player.load(musicFile) }
    LaunchedEffect(Unit) {
        while (isActive) {
            player.tick()
            delay(16)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            bleVm.stopTimeline()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    // 저장하지 않은 편집이 있으면 나가기 전에 확인한다 (시스템 뒤로가기 포함)
    BackHandler(enabled = dirty) { showLeaveDialog = true }
    fun requestLeave() {
        if (dirty) showLeaveDialog = true else onNavigateBack()
    }

    LaunchedEffect(editMessage) {
        editMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            editVm.consumeMessage()
        }
    }
    LaunchedEffect(player.error) {
        player.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // 추가/이동 직후 해당 블록을 선택 상태로 만든다
    LaunchedEffect(entries) {
        pendingSelectTs?.let { ts ->
            selectedIndex = entries.indexOfFirst { it.timestampMs == ts }
            pendingSelectTs = null
        }
        if (selectedIndex >= entries.size) selectedIndex = -1
    }

    val durationMs: Long = analysis?.durationMs
        ?: player.durationMs.takeIf { it > 0L }
        ?: max((entries.lastOrNull()?.timestampMs ?: 0L) + 10_000L, 30_000L)

    // ── 실기기 동기화 ─────────────────────────────────────────
    val isPlaying = player.isPlaying
    LaunchedEffect(bleConnected) { deviceLoaded = false }
    LaunchedEffect(isPlaying, bleConnected) {
        if (bleConnected == null) return@LaunchedEffect
        if (isPlaying) {
            if (!deviceLoaded) {
                bleVm.loadTimeline(frames)
                deviceLoaded = true
            } else {
                bleVm.resumeTimeline()
            }
            while (isActive) {
                bleVm.updatePosition(player.positionMs)
                delay(100)
            }
        } else if (deviceLoaded) {
            bleVm.pauseTimeline()
        }
    }
    // 편집으로 타임라인이 바뀌면 기기에도 다시 올린다
    LaunchedEffect(frames) {
        if (deviceLoaded && bleConnected != null) {
            bleVm.loadTimeline(frames)
            if (!player.isPlaying) bleVm.pauseTimeline()
        }
    }

    // ── 동작 ─────────────────────────────────────────────────
    fun seek(ms: Long) {
        val target = ms.coerceIn(0L, durationMs)
        player.seekTo(target)
        if (deviceLoaded) bleVm.updatePosition(target)
    }

    fun snapAt(ms: Long): Long {
        val tolerance = max(120L, (14f / timeline.pxPerMs).toLong())
        return TimelineMath.snap(ms, analysis?.beatTimesMs.orEmpty(), tolerance, snapToBeats)
    }

    /** [ms] 근처 한 박의 길이(ms): 분석된 비트 간격, 없으면 120BPM(500ms) */
    fun beatLengthAt(ms: Long): Long {
        val beats = analysis?.beatTimesMs.orEmpty()
        if (beats.size < 2) return 500L
        val i = beats.indexOfLast { it <= ms }.coerceAtLeast(0)
        val len = if (i + 1 < beats.size) beats[i + 1] - beats[i] else beats[i] - beats[i - 1]
        return len.takeIf { it in 100L..3000L } ?: 500L
    }

    fun addPreset(preset: EffectPreset, timestampMs: Long) {
        val ts = timestampMs.coerceAtLeast(0L)
        // 이펙트 라이브러리(패턴)와 연결된 칸: 모든 단계를 박자 길이에 맞춰 한 번에 넣는다
        preset.patternId?.let { id ->
            val pattern = patterns.firstOrNull { it.id == id }
            if (pattern == null) {
                Toast.makeText(context, "연결된 이펙트를 찾을 수 없습니다. 설정의 이펙트 라이브러리를 확인해 주세요.", Toast.LENGTH_SHORT).show()
                return
            }
            // 색 역할은 마지막으로 쓴 팔레트 세트로 풀어 넣는다 (선택한 적이 없으면 기본 팔레트)
            val palette = setsVm.paletteSets.value.firstOrNull { it.id == setsVm.lastPaletteSetId }
            pendingSelectTs = ts
            editVm.addTimelineEntries(PatternResolver.expand(pattern, ts, beatLengthAt(ts), palette))
            return
        }
        val existing = entries.indexOfFirst { it.timestampMs == ts }
        val entry = preset.toEntry(ts)
        pendingSelectTs = ts
        if (existing >= 0) editVm.updateTimelineEntry(existing, entry) else editVm.addTimelineEntry(entry)
    }

    fun handleDrop() {
        val preset = drag.preset ?: return
        val p = drag.pointerWindow
        drag.preset = null
        if (timelineBounds.contains(p)) {
            val ms = timeline.msAtX(p.x - timelineBounds.left).toLong()
            addPreset(preset, snapAt(ms))
        }
    }

    val dropPreviewMs: Long? = drag.preset?.let {
        if (timelineBounds.contains(drag.pointerWindow)) {
            snapAt(timeline.msAtX(drag.pointerWindow.x - timelineBounds.left).toLong())
        } else null
    }

    // 곡 하나를 고르는 선택기: 설정의 "음악 불러오기 위치" 에서 시작한다
    val musicPicker = rememberLauncherForActivityResult(OpenMusicDocuments(multiple = false)) { uris ->
        uris.firstOrNull()?.let { editVm.setMusicFile(it) }
    }

    val rightScroll = rememberScrollState()

    // ── 화면 ─────────────────────────────────────────────────
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditorTopBar(
                name = metadata?.name ?: "",
                musicUriString = musicFile?.let { android.net.Uri.fromFile(it).toString() },
                musicLabel = if (musicFile != null) "음악 변경" else "음악 선택",
                analyzing = analyzing,
                applying = applying,
                entryCount = entries.size,
                deviceConnected = bleConnected != null,
                canUndo = canUndo,
                canRedo = canRedo,
                dirty = dirty,
                saving = saving,
                onUndo = { editVm.undo() },
                onRedo = { editVm.redo() },
                onSave = { editVm.save() },
                onApplySets = { showApplyDialog = true },
                onBack = { requestLeave() },
                onRename = { showNameDialog = true },
                onPickMusic = { musicPicker.launch(MusicFolder.initialUri(context)) },
                onDevice = { devicePopup = devicePopupFor(bleConnected) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .onGloballyPositioned { rootOrigin = it.positionInWindow() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 좌측: 타임라인 + 라이브러리
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    // 타임라인(시간/파형/섹션/비트/이펙트 5행)이 커진 만큼 라이브러리는 그 아래에 여유를 두고 배치
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                            .padding(8.dp)
                    ) {
                        TimelineToolbar(
                            snapToBeats = snapToBeats,
                            showBeats = showBeats,
                            followPlayhead = followPlayhead,
                            onShowBeatsChange = { showBeats = it },
                            onSnapChange = { snapToBeats = it },
                            onFollowChange = { followPlayhead = it },
                            onZoomIn = { timeline.zoom(1.5f) },
                            onZoomOut = { timeline.zoom(1f / 1.5f) },
                            onFit = {
                                if (timeline.viewWidthPx > 0f) {
                                    timeline.pxPerMs = timeline.minPxPerMs
                                    timeline.viewStartMs = 0f
                                }
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        TimelineView(
                            state = timeline,
                            entries = entries,
                            analysis = analysis,
                            durationMs = durationMs,
                            positionProvider = { player.positionMs },
                            selectedIndex = selectedIndex,
                            dropPreviewMs = dropPreviewMs,
                            snapToBeats = snapToBeats,
                            showBeats = showBeats,
                            followPlayhead = followPlayhead,
                            isPlaying = isPlaying,
                            emptyHint = when {
                                analyzing -> "음악 분석 중… (파형·섹션·비트)"
                                musicFile == null -> "음악을 선택하면 파형과 섹션이 표시됩니다"
                                else -> "분석 결과가 없습니다"
                            },
                            onSeek = { seek(it) },
                            onEntryClick = { selectedIndex = it },
                            onEntryMove = { index, newMs ->
                                pendingSelectTs = newMs
                                val moved = entries.getOrNull(index) ?: return@TimelineView
                                val clash = entries.indexOfFirst { it.timestampMs == newMs && it !== moved }
                                if (clash >= 0) editVm.deleteTimelineEntry(clash)
                                editVm.updateTimelineEntry(
                                    if (clash in 0 until index) index - 1 else index,
                                    moved.copy(timestampMs = newMs)
                                )
                            },
                            onBoundsChanged = { timelineBounds = it }
                        )
                        TimelineScrollBar(timeline = timeline, durationMs = durationMs)
                    }

                    EffectLibraryPanel(
                        drag = drag,
                        userSlots = userSlots,
                        onTapPreset = { addPreset(it, snapAt(player.positionMs)) },
                        onDrop = { handleDrop() },
                        onUserSlotClick = { libraryEditorSlot = it },
                        onMoveSlot = { from, to -> libVm.move(from, to) },
                        onDeleteSlots = { libVm.deleteAll(it) },
                        patterns = patterns,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                    )
                }

                // 우측: Preview + 재생 + 선택 이펙트
                Box(modifier = Modifier.width(320.dp).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rightScroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewPanel(
                        player = player,
                        entries = entries,
                        analysis = analysis,
                        deviceConnected = bleConnected != null,
                        durationMs = durationMs,
                        onToggle = { player.toggle() },
                        onPrev = {
                            // 현재 섹션 시작에서 2초 넘게 지났으면 그 섹션 처음으로, 아니면 이전 섹션 시작으로
                            val starts = analysis?.sections?.map { it.startMs }.orEmpty()
                            seek(starts.lastOrNull { it < player.positionMs - 2_000L } ?: 0L)
                        },
                        onNext = {
                            val starts = analysis?.sections?.map { it.startMs }.orEmpty()
                            seek(starts.firstOrNull { it > player.positionMs + 100L } ?: durationMs)
                        },
                        onSeek = { seek(it) }
                    )
                    SelectedEntryPanel(
                        entry = entries.getOrNull(selectedIndex),
                        onSaveToLibrary = {
                            entries.getOrNull(selectedIndex)?.let { e ->
                                val slot = libVm.addFromEntry(e)
                                Toast.makeText(
                                    context,
                                    if (slot >= 0) "내 라이브러리 ${slot + 1}번 칸에 저장했습니다."
                                    else "내 라이브러리가 가득 찼습니다 (${UserEffect.SLOT_COUNT}칸). 편집 모드에서 칸을 비워 주세요.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onEdit = { entries.getOrNull(selectedIndex)?.let { editVm.startEditingEntry(selectedIndex, it) } },
                        onDelete = {
                            if (selectedIndex in entries.indices) {
                                editVm.deleteTimelineEntry(selectedIndex)
                                selectedIndex = -1
                            }
                        }
                    )
                }
                SlimVerticalScrollBar(rightScroll, Modifier.align(Alignment.CenterEnd))
                }
            }

        // 드래그 중인 프리셋 고스트
        drag.preset?.let { preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (drag.pointerWindow.x - rootOrigin.x).roundToInt() - 40,
                            (drag.pointerWindow.y - rootOrigin.y).roundToInt() - 70
                        )
                    }
                    // 들어 올린 느낌: 그림자 + 살짝 확대
                    .graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }
                    .shadow(10.dp, MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                PresetSwatch(preset)
                Spacer(Modifier.width(8.dp))
                Text(preset.label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
        }
        }
    }

    // ── 다이얼로그 ───────────────────────────────────────────
    editingEntry?.let { (index, entry) ->
        TimelineEntryDialog(
            entry = entry,
            lastEntryTimestamp = entries.lastOrNull()?.timestampMs ?: 0L,
            onDismiss = { editVm.cancelEditingEntry() },
            onSave = { updated ->
                pendingSelectTs = updated.timestampMs
                editVm.updateTimelineEntry(index, updated)
            },
            onDelete = {
                editVm.deleteTimelineEntry(index)
                selectedIndex = -1
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("저장하지 않은 변경") },
            text = { Text("타임라인에 저장하지 않은 편집이 있습니다. 저장하고 나갈까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    editVm.save(onSaved = onNavigateBack)
                }) { Text("저장하고 나가기") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        onNavigateBack()
                    }) { Text("저장 안 함", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showLeaveDialog = false }) { Text("취소") }
                }
            }
        )
    }

    if (showNameDialog) {
        var text by remember { mutableStateOf(metadata?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("프로젝트 이름") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        editVm.updateProjectName(text.trim())
                        showNameDialog = false
                    }
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("취소") } }
        )
    }

    libraryEditorSlot?.let { slot ->
        val existing = userSlots.getOrNull(slot)
        UserEffectEditorDialog(
            slot = slot,
            initial = existing,
            newId = libVm::newId,
            onSave = {
                libVm.save(slot, it)
                libraryEditorSlot = null
            },
            onDelete = if (existing != null) {
                {
                    libVm.delete(slot)
                    libraryEditorSlot = null
                }
            } else null,
            onDismiss = { libraryEditorSlot = null },
            patterns = patterns
        )
    }

    if (showApplyDialog) {
        // -1 = 곡 전체, 그 외 = analysis.sections 의 인덱스
        var scopeIndex by remember { mutableIntStateOf(-1) }
        val sections = analysis?.sections.orEmpty()
        val scopeRange: LongRange = if (scopeIndex < 0) 0L..Long.MAX_VALUE
        else sections.getOrNull(scopeIndex)?.let { it.startMs until it.endMs } ?: LongRange.EMPTY
        val replaceCount = entries.count { it.timestampMs in scopeRange }
        val ready = analysis?.engineInput != null

        SetSelectionDialog(
            title = "이펙트 세트 적용",
            confirmLabel = "적용",
            effectSets = effectSets,
            paletteSets = paletteSets,
            initialEffectId = setsVm.lastEffectSetId,
            initialPaletteId = setsVm.lastPaletteSetId,
            confirmEnabled = ready && !analyzing,
            onConfirm = { effectId, paletteId ->
                val options = setsVm.rememberSelection(effectId, paletteId)
                editVm.applySets(options, if (scopeIndex < 0) null else scopeIndex)
                showApplyDialog = false
            },
            onManageSets = {
                showApplyDialog = false
                onNavigateToSets()
            },
            onDismiss = { showApplyDialog = false },
            extraContent = {
                SetDropdown(
                    label = "적용 범위",
                    autoLabel = "곡 전체",
                    items = sections.withIndex().toList(),
                    selectedId = if (scopeIndex < 0) "" else scopeIndex.toString(),
                    idOf = { it.index.toString() },
                    nameOf = { (i, sec) ->
                        "${i + 1}. ${sec.type}  ${TimelineMath.formatTime(sec.startMs)}~${TimelineMath.formatTime(sec.endMs)}"
                    },
                    onSelect = { scopeIndex = it.toIntOrNull() ?: -1 }
                )
                Text(
                    when {
                        !ready && analyzing -> "음악 분석 중입니다. 끝나면 적용할 수 있습니다."
                        !ready -> "음악을 선택해 분석한 뒤 적용할 수 있습니다."
                        replaceCount > 0 -> "범위 안의 기존 엔트리 ${replaceCount}개가 새로 만든 이펙트로 교체됩니다."
                        else -> "범위 안에 기존 엔트리가 없어 새로 추가됩니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready && replaceCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }

    // 디바이스 연결(미연결) / 관리(연결) 팝업 — 메인 화면, 설정과 같은 UI
    DevicePopups(bleVm, devicePopup) { devicePopup = DevicePopup.NONE }
}

/** 타임라인 좌우 스크롤바 — 위치 상태를 이 컴포저블 안에서만 읽어 화면 전체가 다시 그려지지 않게 한다. */
@Composable
private fun TimelineScrollBar(timeline: TimelineState, durationMs: Long) {
    val viewMs = if (timeline.pxPerMs > 0f) timeline.viewWidthPx / timeline.pxPerMs else 0f
    SlimHorizontalScrollBar(
        viewStartMs = timeline.viewStartMs,
        viewMs = viewMs,
        durationMs = durationMs,
        onScrollTo = { timeline.viewStartMs = it }
    )
}

// ─────────────────────────────────────────────────────────────
// 상단 바 / 툴바
// ─────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    name: String,
    musicUriString: String?,
    musicLabel: String,
    analyzing: Boolean,
    applying: Boolean,
    entryCount: Int,
    deviceConnected: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    dirty: Boolean,
    saving: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onApplySets: () -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onPickMusic: () -> Unit,
    onDevice: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 곡 이미지(앨범 아트): 타이틀 줄 높이 안에 들어가는 크기
                SongArt(musicUriString = musicUriString, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = name.ifBlank { "EFX" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onRename)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Text(
                    "· $entryCount entries",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AnimatedVisibility(
                    visible = dirty,
                    enter = fadeIn(tween(EfxMotion.MEDIUM, easing = EfxMotion.StandardDecelerate)) +
                        expandHorizontally(tween(EfxMotion.MEDIUM, easing = EfxMotion.Emphasized)),
                    exit = fadeOut(tween(EfxMotion.SHORT)) +
                        shrinkHorizontally(tween(EfxMotion.MEDIUM, easing = EfxMotion.Emphasized))
                ) {
                    Text(
                        "저장 안 됨",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        actions = {
            if (analyzing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "분석 중…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
            }
            // 실행 취소 / 다시 실행: Material Symbols 의 Undo / Redo
            FilledTonalIconButton(onClick = onUndo, enabled = canUndo) {
                Icon(HistoryIcons.Undo, contentDescription = "실행 취소", modifier = Modifier.size(22.dp))
            }
            FilledTonalIconButton(onClick = onRedo, enabled = canRedo) {
                Icon(HistoryIcons.Redo, contentDescription = "다시 실행", modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = dirty && !saving) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("저장")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onApplySets, enabled = !applying) {
                if (applying) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (applying) "적용 중…" else "세트 적용")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onPickMusic) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(musicLabel)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onDevice) {
                Icon(
                    if (deviceConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (deviceConnected) "기기 연결됨" else "기기 연결")
            }
            Spacer(Modifier.width(12.dp))
        }
    )
}

@Composable
private fun TimelineToolbar(
    snapToBeats: Boolean,
    showBeats: Boolean,
    followPlayhead: Boolean,
    onShowBeatsChange: (Boolean) -> Unit,
    onSnapChange: (Boolean) -> Unit,
    onFollowChange: (Boolean) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("타임라인", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        FilterChip(selected = showBeats, onClick = { onShowBeatsChange(!showBeats) }, label = { Text("비트 표시") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = snapToBeats, onClick = { onSnapChange(!snapToBeats) }, label = { Text("비트 스냅") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = followPlayhead, onClick = { onFollowChange(!followPlayhead) }, label = { Text("따라가기") })
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, contentDescription = "축소") }
        IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, contentDescription = "확대") }
        TextButton(onClick = onFit) { Text("전체 보기") }
    }
}

// ─────────────────────────────────────────────────────────────
// 우측 패널
// ─────────────────────────────────────────────────────────────

/**
 * Preview 패널 — 왼쪽에 LED 아이콘(작게), 오른쪽에 컨트롤 패널(재생 바 + 버튼)을 가로로 배치한다.
 * 컨트롤은 Light-Stick-Music 의 재생 바와 같은 모양이다. 이전/다음 버튼은 곡 목록이 없으므로
 * 이전/다음 **섹션 시작**으로 이동한다 (분석 전에는 처음/끝).
 */
@Composable
private fun PreviewPanel(
    player: MusicPlayerController,
    entries: List<EfxEntry>,
    analysis: MusicAnalysis?,
    deviceConnected: Boolean,
    durationMs: Long,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val position = player.positionMs   // 이 컴포저블만 재생 위치에 따라 다시 그려진다
    val led = LedSimulator.stateAt(entries, position)
    val current = entries.getOrNull(LedSimulator.indexAt(entries, position))
    val section = analysis?.sections?.firstOrNull { position >= it.startMs && position < it.endMs }
    val canPlay = player.isReady

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽: LED 아이콘 + 현재 이펙트 / 섹션
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(PREVIEW_ICON_SIZE)) {
            LedPreview(color = led, modifier = Modifier.width(PREVIEW_ICON_SIZE))
            Spacer(Modifier.height(4.dp))
            Text(
                text = current?.payload?.effectType?.name ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = listOfNotNull(section?.type, analysis?.musicStyle).joinToString(" · ").ifEmpty { " " },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(12.dp))

        // 오른쪽: 컨트롤 패널
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (deviceConnected) {
                    Text("● 실기기 동기화", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.height(6.dp))
            PlayerSeekBar(currentPosition = position, duration = durationMs, onSeekTo = onSeek)
            Spacer(Modifier.height(6.dp))
            PlayerButtons(
                isPlaying = player.isPlaying,
                enabled = canPlay,
                onPrev = onPrev,
                onPlayPause = onToggle,
                onNext = onNext,
                scale = 0.75f
            )
            if (!canPlay) {
                Text(
                    "음악을 선택하면 재생할 수 있습니다",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectedEntryPanel(
    entry: EfxEntry?,
    onSaveToLibrary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 선택/해제로 행이 늘고 줄 때 패널 높이가 부드럽게 바뀐다
            .animateContentSize(tween(EfxMotion.MEDIUM, easing = EfxMotion.Emphasized))
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(12.dp)
    ) {
        Text("선택한 이펙트", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        if (entry == null) {
            Text(
                "타임라인의 블록을 탭하면 속성을 볼 수 있습니다.\n블록을 끌면 시간을 옮길 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        val p = entry.payload
        // (라벨, 값, 값 옆에 보여줄 색 견본)
        val rows: List<Triple<String, String, Color?>> = listOf(
            Triple("시각", TimelineMath.formatTime(entry.timestampMs, withTenths = true), null),
            Triple("종류", p.effectType.name, null),
            Triple("색상 (FG)", "RGB(${p.color.r}, ${p.color.g}, ${p.color.b})", Color(p.color.r, p.color.g, p.color.b)),
            Triple(
                "배경 (BG)",
                "RGB(${p.backgroundColor.r}, ${p.backgroundColor.g}, ${p.backgroundColor.b})",
                Color(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b)
            ),
            // 같은 패킷 필드: ON/OFF 는 Transit, BLINK/STROBE/BREATH 는 Period. 숫자는 프레임 수이고 1프레임 길이는 SPF 가 정한다
            Triple(TimelineMath.periodLabel(p.effectType), TimelineMath.formatFrames(p.period, p.spf), null),
            Triple("SPF", "${p.spf}  (${TimelineMath.frameUnitLabel(p.spf).substringBefore(" (")})", null),
            Triple("Fade (밝기)", TimelineMath.formatFade(p.fade), null),
            Triple("그룹 마스크", TimelineMath.formatGroupMask(p.groupMask), null),
            Triple(
                "Random Delay",
                if (p.randomDelay == 0) "0f  (없음)" else "최대 " + TimelineMath.formatFrames(p.randomDelay, p.spf),
                null
            ),
            Triple("Random Color", if (p.randomColor != 0) "ON" else "OFF", null),
            Triple("Index", p.effectIndex.toString(), null)
        )
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            rows.forEach { (k, v, swatch) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        k,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp)
                    )
                    if (swatch != null) {
                        // 색상 수치값 옆의 색 견본 (어두운 색도 배경과 구분되도록 테두리)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(swatch, MaterialTheme.shapes.extraSmall)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        v,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (k == "Random Color" && p.randomColor != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("편집")
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("삭제")
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onSaveToLibrary) {
            Text("내 라이브러리에 저장")
        }
    }
}
