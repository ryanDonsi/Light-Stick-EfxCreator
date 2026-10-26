package com.efxcreator.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
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
import com.efxcreator.ui.TimelineEntryDialog
import com.efxcreator.ui.sets.SetDropdown
import com.efxcreator.ui.sets.SetSelectionDialog
import com.efxcreator.viewmodel.BleViewModel
import com.efxcreator.viewmodel.EffectLibraryViewModel
import com.efxcreator.viewmodel.EfxEditViewModel
import com.efxcreator.viewmodel.SetsViewModel
import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.roundToInt

private val EDITOR_BG = Color(0xFF101014)
private val PANEL_BG = Color(0xFF1B1B21)
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
    bleVm: BleViewModel = viewModel(),
    setsVm: SetsViewModel = viewModel(),
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
    val userSlots by libVm.slots.collectAsState()
    val effectSets by setsVm.effectSets.collectAsState()
    val paletteSets by setsVm.paletteSets.collectAsState()

    val bleConnected by bleVm.connected.collectAsState()
    val bleMessage by bleVm.message.collectAsState()

    val entries: List<EfxEntry> = efx?.body?.entries.orEmpty()
    val frames = remember(entries) { entries.map { it.timestampMs to it.payload.toByteArray() } }

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
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showApplyDialog by remember { mutableStateOf(false) }
    // 내 라이브러리에서 추가/수정 중인 칸 번호 (null = 닫힘)
    var libraryEditorSlot by remember { mutableStateOf<Int?>(null) }
    var deviceLoaded by remember { mutableStateOf(false) }
    var scanAfterPermission by remember { mutableStateOf(false) }

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

    LaunchedEffect(editMessage) {
        editMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            editVm.consumeMessage()
        }
    }
    LaunchedEffect(bleMessage) {
        bleMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            bleVm.consumeMessage()
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

    fun addPreset(preset: EffectPreset, timestampMs: Long) {
        val ts = timestampMs.coerceAtLeast(0L)
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            if (scanAfterPermission) bleVm.startScan()
        } else {
            Toast.makeText(context, "블루투스 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
        scanAfterPermission = false
    }

    fun requestScan() {
        if (bleVm.hasPermissions()) {
            bleVm.startScan()
        } else {
            scanAfterPermission = true
            permissionLauncher.launch(BleViewModel.requiredPermissions())
        }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editVm.setMusicFile(uri)
    }

    // ── 화면 ─────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EDITOR_BG)
            .onGloballyPositioned { rootOrigin = it.positionInWindow() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            EditorTopBar(
                name = metadata?.name ?: "",
                musicLabel = if (musicFile != null) "음악 변경" else "음악 선택",
                analyzing = analyzing,
                applying = applying,
                entryCount = entries.size,
                deviceConnected = bleConnected != null,
                onApplySets = { showApplyDialog = true },
                onBack = onNavigateBack,
                onRename = { showNameDialog = true },
                onPickMusic = { musicPicker.launch("audio/*") },
                onDevice = { showDeviceDialog = true }
            )

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
                            .background(PANEL_BG, RoundedCornerShape(10.dp))
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
                                    timeline.pxPerMs = (timeline.viewWidthPx / durationMs.toFloat())
                                        .coerceIn(TimelineState.MIN_PX_PER_MS, TimelineState.MAX_PX_PER_MS)
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
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(PANEL_BG, RoundedCornerShape(10.dp))
                    )
                }

                // 우측: Preview + 재생 + 선택 이펙트
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
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
                    .background(Color(0xE6303040), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                PresetSwatch(preset)
                Spacer(Modifier.width(8.dp))
                Text(preset.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
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
            onDismiss = { libraryEditorSlot = null }
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

    if (showDeviceDialog) {
        DeviceDialog(
            bleVm = bleVm,
            onScan = { requestScan() },
            onDismiss = {
                bleVm.stopScan()
                showDeviceDialog = false
            }
        )
    }
}

/** 타임라인 좌우 스크롤바 — 위치 상태를 이 컴포저블 안에서만 읽어 화면 전체가 다시 그려지지 않게 한다. */
@Composable
private fun TimelineScrollBar(timeline: TimelineState, durationMs: Long) {
    val viewMs = if (timeline.pxPerMs > 0f) timeline.viewWidthPx / timeline.pxPerMs else 0f
    val maxStart = max(1f, durationMs - viewMs * 0.5f)
    Slider(
        value = timeline.viewStartMs.coerceIn(0f, maxStart),
        onValueChange = { timeline.viewStartMs = it },
        valueRange = 0f..maxStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    )
}

// ─────────────────────────────────────────────────────────────
// 상단 바 / 툴바
// ─────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    name: String,
    musicLabel: String,
    analyzing: Boolean,
    applying: Boolean,
    entryCount: Int,
    deviceConnected: Boolean,
    onApplySets: () -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onPickMusic: () -> Unit,
    onDevice: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(PANEL_BG)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
        }
        Text(
            text = name.ifBlank { "EFX" },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .clickable(onClick = onRename)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
        Text("· $entryCount entries", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9A9AA5))

        Spacer(Modifier.weight(1f))

        if (analyzing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("분석 중…", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB0B0BA))
            Spacer(Modifier.width(12.dp))
        }
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
    }
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
        Text("타임라인", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.weight(1f))
        FilterChip(selected = showBeats, onClick = { onShowBeatsChange(!showBeats) }, label = { Text("비트 표시") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = snapToBeats, onClick = { onSnapChange(!snapToBeats) }, label = { Text("비트 스냅") })
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = followPlayhead, onClick = { onFollowChange(!followPlayhead) }, label = { Text("따라가기") })
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, contentDescription = "축소", tint = Color.White) }
        IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, contentDescription = "확대", tint = Color.White) }
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
            .background(PANEL_BG, RoundedCornerShape(10.dp))
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
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = listOfNotNull(section?.type, analysis?.musicStyle).joinToString(" · ").ifEmpty { " " },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9A9AA5),
                maxLines = 1
            )
        }

        Spacer(Modifier.width(12.dp))

        // 오른쪽: 컨트롤 패널
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Preview", style = MaterialTheme.typography.labelLarge, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (deviceConnected) {
                    Text("● 실기기 동기화", style = MaterialTheme.typography.labelSmall, color = Color(0xFF66BB6A))
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
                    color = Color(0xFF8A8A95)
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
            .background(PANEL_BG, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("선택한 이펙트", style = MaterialTheme.typography.titleSmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        if (entry == null) {
            Text(
                "타임라인의 블록을 탭하면 속성을 볼 수 있습니다.\n블록을 끌면 시간을 옮길 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8A8A95)
            )
            return@Column
        }
        val p = entry.payload
        val rows = listOf(
            "시각" to TimelineMath.formatTime(entry.timestampMs, withTenths = true),
            "종류" to p.effectType.name,
            "색상" to "RGB(${p.color.r}, ${p.color.g}, ${p.color.b})",
            "배경" to "RGB(${p.backgroundColor.r}, ${p.backgroundColor.g}, ${p.backgroundColor.b})",
            "Period" to p.period.toString(),
            "SPF / Fade" to "${p.spf} / ${p.fade}",
            "Index" to p.effectIndex.toString()
        )
        rows.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(k, style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A8A95), modifier = Modifier.width(84.dp))
                Text(v, style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
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

// ─────────────────────────────────────────────────────────────
// 기기 연결 다이얼로그
// ─────────────────────────────────────────────────────────────

@Composable
private fun DeviceDialog(
    bleVm: BleViewModel,
    onScan: () -> Unit,
    onDismiss: () -> Unit
) {
    val scanning by bleVm.scanning.collectAsState()
    val found by bleVm.found.collectAsState()
    val connected by bleVm.connected.collectAsState()
    val connectingMac by bleVm.connectingMac.collectAsState()
    val hasScanned by bleVm.hasScanned.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("응원봉 연결") },
        text = {
            Column(modifier = Modifier.width(360.dp)) {
                connected?.let { device ->
                    Text(
                        "연결됨: ${device.name ?: device.mac}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "재생하면 편집 중인 이펙트가 이 기기로 음악과 동기화되어 전송됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { bleVm.disconnect() }) { Text("연결 해제") }
                    Spacer(Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onScan, enabled = !scanning) { Text(if (scanning) "검색 중…" else "기기 검색") }
                    if (scanning) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (hasScanned && !scanning && found.isEmpty()) {
                    Text(
                        "검색된 응원봉이 없습니다. 아래를 확인해 주세요.\n" +
                            "• 응원봉 전원이 켜져 있고 연결 대기(광고) 상태인지\n" +
                            "• 다른 앱(Light Stick Music 등)이나 다른 기기에 이미 연결돼 있지 않은지 — 연결된 응원봉은 검색되지 않습니다\n" +
                            "• 기기 이름이 LS / GL / RL 로 끝나는지\n" +
                            "• 블루투스가 켜져 있는지 (Android 11 이하는 위치도)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(found, key = { it.mac }) { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = connectingMac == null) { bleVm.connect(device) }
                                .padding(vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name ?: "(이름 없음)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    device.mac,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (connectingMac == device.mac) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                device.rssi?.let {
                                    Text("$it dBm", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}
