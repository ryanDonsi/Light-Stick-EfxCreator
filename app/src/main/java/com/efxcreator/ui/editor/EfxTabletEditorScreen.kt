@file:OptIn(ExperimentalMaterial3Api::class)

package com.efxcreator.ui.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import com.efxcreator.ui.MusicFolder
import com.efxcreator.ui.activityViewModel
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
import com.efxcreator.viewmodel.EfxEditViewModel
import com.efxcreator.viewmodel.PatternsViewModel
import com.efxcreator.viewmodel.SetsViewModel
import com.efxcreator.model.PatternResolver
import com.efxcreator.model.PatternStep
import com.lightstick.efx.EfxEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.roundToInt
import com.efxcreator.R
import com.efxcreator.core.Loc

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
    onNavigateToEffectLibrary: () -> Unit = {},
    editVm: EfxEditViewModel = viewModel(),
    bleVm: BleViewModel = activityBleViewModel(),
    setsVm: SetsViewModel = viewModel(),
    patternsVm: PatternsViewModel = activityViewModel()
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
    val effectSets by setsVm.effectSets.collectAsState()
    val paletteSets by setsVm.paletteSets.collectAsState()
    // 팔레트/이펙트 세트가 "기본/자동"일 때 실제로 적용되는 값: 곡의 장르와 엔진이 고르는 색 계열
    val autoStyle = analysis?.engineInput?.musicStyle
    val autoPalette = remember(autoStyle, efx?.header?.musicId) {
        val style = autoStyle?.let { runCatching { com.efxcreator.music.style.MusicStyleClassifier.MusicStyle.valueOf(it) }.getOrNull() }
        val seed = efx?.header?.musicId
        if (style == null || seed == null) null
        else runCatching { com.efxcreator.music.effect.EffectMatchingEngineRouter.createEngine().buildPalette(seed, style) }.getOrNull()
    }
    // 곡별 세트 선택은 설정값이라 상태가 아니므로, 적용할 때마다 올려서 표시를 갱신한다
    var songSetsRev by remember { mutableIntStateOf(0) }
    // 이 곡의 실제 팔레트: 고른 팔레트 세트, 없으면 엔진이 곡에 맞춰 고른 색 계열("기본").
    // 이펙트 라이브러리 칸의 색과, 칸을 타임라인에 넣을 때의 색이 모두 이 팔레트를 따른다.
    val effectivePalette = remember(songSetsRev, paletteSets, autoPalette) {
        val id = setsVm.songPaletteSetId(projectId)
        paletteSets.firstOrNull { it.id == id } ?: autoPalette?.roleColors?.takeIf { it.size >= 4 }?.let { c ->
            com.efxcreator.music.sets.PaletteSet(
                id = "auto", name = "auto",
                main = com.efxcreator.music.sets.RgbColor(c[0].r, c[0].g, c[0].b),
                step1 = com.efxcreator.music.sets.RgbColor(c[1].r, c[1].g, c[1].b),
                step2 = com.efxcreator.music.sets.RgbColor(c[2].r, c[2].g, c[2].b),
                step3 = com.efxcreator.music.sets.RgbColor(c[3].r, c[3].g, c[3].b)
            )
        }
    }

    val bleConnected by bleVm.connected.collectAsState()
    val patterns by patternsVm.patterns.collectAsState()

    val entries: List<EfxEntry> = efx?.body?.entries.orEmpty()
    // 실기기로 보내는 프레임 (Broadcasting Mode 는 SDK 2.1.0 부터 기기 설정이라 페이로드에는 반영하지 않는다)
    val frames = remember(entries) {
        entries.map { it.timestampMs to it.payload.toByteArray() }
    }

    // ── 재생 / 타임라인 상태 ──────────────────────────────────
    // 곡 목록에서 재생 중에 들어왔다면 그 플레이어를 그대로 이어받아 음악이 끊기지 않게 한다
    val player = remember { com.efxcreator.ui.PlaybackHandoff.peekController(projectId) ?: MusicPlayerController() }
    val timeline = remember { TimelineState() }
    val drag = remember { PresetDragState() }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    // 선택 모드(복수 선택): 선택한 이펙트를 시각(ms)으로 기억한다 — 이동/편집으로 순서가 바뀌어도 선택이 유지된다
    var selectMode by remember { mutableStateOf(false) }
    var multiTimes by remember { mutableStateOf<Set<Long>>(emptySet()) }
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
    var deviceLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { editVm.loadProject(projectId) }
    LaunchedEffect(musicFile) {
        val musicFile = musicFile   // 위임 프로퍼티라 스마트 캐스트가 안 되므로 지역 변수로 복사
        // 이어받은 플레이어가 이미 이 곡을 불러와 재생 중이면 다시 불러오지 않는다
        if (musicFile != null && player.loadedPath == musicFile.absolutePath && player.isReady) {
            com.efxcreator.ui.PlaybackHandoff.take(projectId)
            return@LaunchedEffect
        }
        player.load(musicFile)
        // 곡 목록에서 재생 중에 들어왔다면 같은 위치에서 이어서 재생한다
        val handoff = if (musicFile != null) com.efxcreator.ui.PlaybackHandoff.take(projectId) else null
        if (handoff != null) {
            var waited = 0
            while (!player.isReady && player.error == null && waited < 3_000) {
                delay(50)
                waited += 50
            }
            if (player.isReady) {
                player.seekTo(handoff.first)
                if (handoff.second) player.play()
            }
        }
    }
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
        // 지워졌거나 사라진 이펙트는 선택에서 뺀다
        val existing = entries.map { it.timestampMs }.toSet()
        if (!existing.containsAll(multiTimes)) multiTimes = multiTimes.filter { it in existing }.toSet()
    }

    val durationMs: Long = analysis?.durationMs
        ?: player.durationMs.takeIf { it > 0L }
        ?: max((entries.lastOrNull()?.timestampMs ?: 0L) + 10_000L, 30_000L)

    // ── 실기기 동기화 ─────────────────────────────────────────
    val isPlaying = player.isPlaying
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(bleConnected) { deviceLoaded = false }
    LaunchedEffect(isPlaying, bleConnected) {
        if (bleConnected == null) return@LaunchedEffect
        if (isPlaying) {
            if (!deviceLoaded) {
                bleVm.loadTimeline(frames)
                deviceLoaded = true
            } else {
                bleVm.resumeTimeline(player.positionMs)
            }
            while (isActive) {
                // 타임라인 눈금자를 드래그하는 동안에는 기기로 위치를 보내지 않고, 손을 떼면 한 번 보낸다
                if (!scrubbing) bleVm.updatePosition(player.positionMs)
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
    /** 위치를 확정해 이동한다: 음악과 실기기 모두 그 위치로 맞춘다 */
    fun seek(ms: Long) {
        val target = ms.coerceIn(0L, durationMs)
        player.seekTo(target)
        if (deviceLoaded) bleVm.seekTimeline(target)
    }

    /** 드래그 중 이동: 음악 위치만 따라가고 실기기에는 보내지 않는다 (손을 떼면 [seek]) */
    fun scrubTo(ms: Long) {
        scrubbing = true
        player.seekTo(ms.coerceIn(0L, durationMs))
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
                Toast.makeText(context, Loc.str(R.string.efx_tablet_editor_screen_1), Toast.LENGTH_SHORT).show()
                return
            }
            // 색 역할은 현재 곡에 적용된 팔레트 세트로 풀어 넣는다 (없으면 기본 팔레트)
            val palette = effectivePalette
            pendingSelectTs = ts
            // 시작 위치부터 이펙트 길이(비트 수)만큼의 기존 엔트리는 새 이펙트로 교체된다
            val beatMs = beatLengthAt(ts)
            // 바 단위 색 순환은 곡의 박자 수(분석 결과)로 계산하고, 없으면 4/4 로 본다
            val barBeats = analysis?.engineInput?.beatsPerBar ?: PatternStep.DEFAULT_BEATS_PER_BAR
            editVm.addTimelineEntries(
                PatternResolver.expand(pattern, ts, beatMs, palette, barBeats),
                replaceFromMs = ts,
                replaceToMs = ts + PatternResolver.totalDurationMs(pattern, beatMs, barBeats)
            )
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


    // ── 화면 ─────────────────────────────────────────────────
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EditorTopBar(
                name = metadata?.name ?: "",
                musicUriString = musicFile?.let { android.net.Uri.fromFile(it).toString() },
                musicLabel = if (musicFile != null) Loc.str(R.string.efx_tablet_editor_screen_2) else Loc.str(R.string.efx_edit_screen_25),
                analyzing = analyzing,
                applying = applying,
                entryCount = entries.size,
                genre = analysis?.musicStyle,
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
                            selectMode = selectMode,
                            onSelectModeChange = { on ->
                                if (on) {
                                    // 지금 선택돼 있던 이펙트를 첫 선택으로 가져간다
                                    multiTimes = entries.getOrNull(selectedIndex)?.let { setOf(it.timestampMs) } ?: emptySet()
                                } else {
                                    // 하나만 남았으면 그 이펙트를 일반 선택으로 돌려놓는다
                                    selectedIndex = if (multiTimes.size == 1) entries.indexOfFirst { it.timestampMs == multiTimes.first() } else -1
                                    multiTimes = emptySet()
                                }
                                selectMode = on
                            },
                            autoPaletteFamily = autoPalette?.colorFamily,
                            autoPaletteColors = autoPalette?.roleColors.orEmpty().map { Color(it.r, it.g, it.b) },
                            autoSetName = com.efxcreator.music.sets.SetCatalog.BUILT_IN_RULE_SETS
                                .firstOrNull { it.id == "builtin_energy" }?.name?.substringBefore(" ("),
                            paletteChoices = paletteSets,
                            onSelectPalette = { set ->
                                // 이 곡의 팔레트 세트를 바꾼다 (라이브러리 칸의 색과 앞으로 넣는 이펙트의 색이 따라간다)
                                setsVm.rememberSongPalette(projectId, set?.id)
                                songSetsRev++
                            },
                            paletteSet = songSetsRev.let { paletteSets.firstOrNull { it.id == setsVm.songPaletteSetId(projectId) } },
                            effectSetName = effectSets.firstOrNull { it.id == setsVm.songEffectSetId(projectId) }?.name,
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
                            selectMode = selectMode,
                            selectedIndices = remember(entries, multiTimes) {
                                entries.indices.filter { entries[it].timestampMs in multiTimes }.toSet()
                            },
                            onEntryToggle = { idx ->
                                val t = entries.getOrNull(idx)?.timestampMs
                                if (t != null) multiTimes = if (t in multiTimes) multiTimes - t else multiTimes + t
                            },
                            onSelectionChange = { picked ->
                                multiTimes = picked.mapNotNull { entries.getOrNull(it)?.timestampMs }.toSet()
                            },
                            onLaneLongPress = { idx ->
                                // 길게 누르면 선택 모드로 들어가고, 누른 이펙트를 첫 선택으로 삼는다
                                val t = entries.getOrNull(idx)?.timestampMs
                                if (!selectMode) {
                                    multiTimes = t?.let { setOf(it) } ?: emptySet()
                                    selectMode = true
                                } else if (t != null) {
                                    multiTimes = multiTimes + t
                                }
                            },
                            onEntriesMove = { idxs, delta -> multiTimes = editVm.moveTimelineEntries(idxs, delta) },
                            dropPreviewMs = dropPreviewMs,
                            snapToBeats = snapToBeats,
                            showBeats = showBeats,
                            followPlayhead = followPlayhead,
                            isPlaying = isPlaying,
                            emptyHint = when {
                                analyzing -> Loc.str(R.string.efx_tablet_editor_screen_3)
                                musicFile == null -> Loc.str(R.string.efx_tablet_editor_screen_4)
                                else -> Loc.str(R.string.efx_tablet_editor_screen_5)
                            },
                            onSeek = { scrubTo(it) },
                            onSeekFinished = { scrubbing = false; seek(it) },
                            onEntryClick = { selectedIndex = it },
                            onEntryMove = { index, newMs ->
                                pendingSelectTs = newMs
                                val moved = entries.getOrNull(index) ?: return@TimelineView
                                // 옮긴 자리에 이펙트가 있으면 지우지 않고 서로 자리를 바꾼다
                                editVm.moveTimelineEntries(listOf(index), newMs - moved.timestampMs)
                            },
                            onBoundsChanged = { timelineBounds = it }
                        )
                        TimelineScrollBar(timeline = timeline, durationMs = durationMs)
                    }

                    EffectLibraryPanel(
                        drag = drag,
                        patterns = patterns,
                        palette = effectivePalette,
                        onTapPreset = { addPreset(it, snapAt(player.positionMs)) },
                        onDrop = { handleDrop() },
                        onManageEffects = onNavigateToEffectLibrary,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                    )
                }

                // 우측: Preview + 재생(고정) / 선택 이펙트(남는 높이를 채우고, 넘치면 이 카드 안에서만 스크롤)
                Column(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
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
                        modifier = Modifier.weight(1f),
                        beatTimesMs = analysis?.beatTimesMs.orEmpty(),
                        entry = if (selectMode) null else entries.getOrNull(selectedIndex),
                        multiEntries = if (selectMode) entries.filter { it.timestampMs in multiTimes } else emptyList(),
                        selectMode = selectMode,
                        onClearSelection = { multiTimes = emptySet(); selectMode = false },
                        onDeleteSelected = {
                            val idxs = entries.indices.filter { entries[it].timestampMs in multiTimes }
                            editVm.deleteTimelineEntries(idxs)
                            multiTimes = emptySet()
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
            // 시작 시각/지속시간 편집: 비트 단위 입력, 비트 스냅(편집 화면 설정과 공유), 덮어쓰기/밀기
            showOverwrite = true,
            onSaveOverwrite = { updated, overwrite ->
                pendingSelectTs = updated.timestampMs
                editVm.updateTimelineEntry(index, updated, overwrite)
            },
            beatTimesMs = analysis?.beatTimesMs.orEmpty(),
            snapToBeats = snapToBeats,
            onSnapChange = { snapToBeats = it },
            otherEntries = entries.filterIndexed { i, _ -> i != index },
            onDelete = {
                editVm.deleteTimelineEntry(index)
                selectedIndex = -1
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(Loc.str(R.string.efx_tablet_editor_screen_6)) },
            text = { Text(Loc.str(R.string.efx_tablet_editor_screen_7)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    editVm.save(onSaved = onNavigateBack)
                }) { Text(Loc.str(R.string.efx_tablet_editor_screen_8)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        onNavigateBack()
                    }) { Text(Loc.str(R.string.efx_tablet_editor_screen_9), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showLeaveDialog = false }) { Text(Loc.str(R.string.efx_edit_screen_16)) }
                }
            }
        )
    }

    if (showNameDialog) {
        var text by remember { mutableStateOf(metadata?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(Loc.str(R.string.efx_tablet_editor_screen_10)) },
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
                ) { Text(Loc.str(R.string.efx_edit_screen_21)) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
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
            title = Loc.str(R.string.efx_tablet_editor_screen_11),
            confirmLabel = Loc.str(R.string.efx_edit_screen_5),
            effectSets = effectSets,
            paletteSets = paletteSets,
            initialEffectId = setsVm.songEffectSetId(projectId),
            initialPaletteId = setsVm.songPaletteSetId(projectId),
            confirmEnabled = ready && !analyzing,
            onConfirm = { effectId, paletteId ->
                val options = setsVm.rememberSelection(effectId, paletteId)
                setsVm.rememberSongPalette(projectId, paletteId)
                setsVm.rememberSongEffectSet(projectId, effectId)
                songSetsRev++
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
                    label = Loc.str(R.string.efx_tablet_editor_screen_12),
                    autoLabel = Loc.str(R.string.efx_tablet_editor_screen_13),
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
                        !ready && analyzing -> Loc.str(R.string.efx_tablet_editor_screen_14)
                        !ready -> Loc.str(R.string.efx_tablet_editor_screen_15)
                        replaceCount > 0 -> Loc.str(R.string.efx_tablet_editor_screen_16, replaceCount)
                        else -> Loc.str(R.string.efx_tablet_editor_screen_17)
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
    genre: String?,
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Loc.str(R.string.efx_edit_screen_2))
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
                // 곡의 장르는 바뀌지 않으므로 제목 옆에 위에서부터 장르 > entries 순으로 표시한다
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    if (!genre.isNullOrBlank()) {
                        Text(
                            genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        "$entryCount entries",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                AnimatedVisibility(
                    visible = dirty,
                    enter = fadeIn(tween(EfxMotion.MEDIUM, easing = EfxMotion.StandardDecelerate)) +
                        expandHorizontally(tween(EfxMotion.MEDIUM, easing = EfxMotion.Emphasized)),
                    exit = fadeOut(tween(EfxMotion.SHORT)) +
                        shrinkHorizontally(tween(EfxMotion.MEDIUM, easing = EfxMotion.Emphasized))
                ) {
                    Text(
                        Loc.str(R.string.efx_tablet_editor_screen_18),
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
                    Loc.str(R.string.efx_tablet_editor_screen_19),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
            }
            // 실행 취소 / 다시 실행: Material Symbols 의 Undo / Redo
            FilledTonalIconButton(onClick = onUndo, enabled = canUndo) {
                Icon(HistoryIcons.Undo, contentDescription = Loc.str(R.string.efx_tablet_editor_screen_20), modifier = Modifier.size(22.dp))
            }
            FilledTonalIconButton(onClick = onRedo, enabled = canRedo) {
                Icon(HistoryIcons.Redo, contentDescription = Loc.str(R.string.efx_tablet_editor_screen_21), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = dirty && !saving) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(Loc.str(R.string.efx_edit_screen_21))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onApplySets, enabled = !applying) {
                if (applying) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (applying) Loc.str(R.string.efx_tablet_editor_screen_22) else Loc.str(R.string.efx_tablet_editor_screen_23))
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
                Text(if (deviceConnected) Loc.str(R.string.efx_tablet_editor_screen_24) else Loc.str(R.string.efx_tablet_editor_screen_25))
            }
            Spacer(Modifier.width(12.dp))
        }
    )
}

/** 켜진 토글 칩은 패널 배경과 잘 구분되도록 앱 포인트 색(primaryContainer)으로 채운다 */
@Composable
private fun toggleChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Composable
private fun TimelineToolbar(
    selectMode: Boolean,
    onSelectModeChange: (Boolean) -> Unit,
    autoPaletteFamily: String?,
    autoPaletteColors: List<Color>,
    autoSetName: String?,
    paletteSet: com.efxcreator.music.sets.PaletteSet?,
    /** 곡의 팔레트로 고를 수 있는 세트들과, 고른 결과(null = 기본/자동) */
    paletteChoices: List<com.efxcreator.music.sets.PaletteSet>,
    onSelectPalette: (com.efxcreator.music.sets.PaletteSet?) -> Unit,
    effectSetName: String?,
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
        Text(Loc.str(R.string.efx_tablet_editor_screen_26), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        // 이 곡에 적용된 팔레트 / 이펙트 세트
        Column(modifier = Modifier.weight(1f)) {
            var paletteMenu by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // 눌러서 이 곡의 팔레트 세트를 고른다
                modifier = Modifier.clickable { paletteMenu = true }
            ) {
                Text(
                    Loc.str(R.string.song_info_palette),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                val dotColors = paletteSet?.colors?.map { Color(it.r, it.g, it.b) } ?: autoPaletteColors
                dotColors.forEach { c ->
                    Box(
                        Modifier
                            .padding(end = 3.dp)
                            .size(12.dp)
                            .background(c, androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                    )
                }
                Spacer(Modifier.width(3.dp))
                Text(
                    // 기본(자동)이면 실제로 선택된 색 계열을 괄호로 덧붙인다. 예: 기본(퍼플)
                    paletteSet?.name ?: autoPaletteFamily?.let { Loc.str(R.string.song_info_detail, Loc.str(R.string.song_info_default), familyLabel(it)) }
                        ?: Loc.str(R.string.song_info_default),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = Loc.str(R.string.song_palette_choose), modifier = Modifier.size(18.dp))
                DropdownMenu(expanded = paletteMenu, onDismissRequest = { paletteMenu = false }) {
                    // 기본(자동): 엔진이 곡에 맞춰 고른 색 계열
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                autoPaletteColors.forEach { c ->
                                    Box(Modifier.padding(end = 3.dp).size(12.dp).background(c, androidx.compose.foundation.shape.CircleShape))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(autoPaletteFamily?.let { Loc.str(R.string.song_info_detail, Loc.str(R.string.song_info_default), familyLabel(it)) }
                                    ?: Loc.str(R.string.song_info_default))
                            }
                        },
                        trailingIcon = { if (paletteSet == null) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = { paletteMenu = false; onSelectPalette(null) }
                    )
                    paletteChoices.forEach { set ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    set.colors.forEach { c ->
                                        Box(Modifier.padding(end = 3.dp).size(12.dp).background(Color(c.r, c.g, c.b), androidx.compose.foundation.shape.CircleShape))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(set.name)
                                }
                            },
                            trailingIcon = { if (paletteSet?.id == set.id) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { paletteMenu = false; onSelectPalette(set) }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Loc.str(R.string.song_info_effect_set),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // 자동은 엔진 기본 동작이며 내장 세트 "에너지 반응" 과 규칙이 같다. 예: 자동(에너지 반응)
                    effectSetName ?: autoSetName?.let { Loc.str(R.string.song_info_detail, Loc.str(R.string.song_info_auto), it) }
                        ?: Loc.str(R.string.song_info_auto),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        FilterChip(selected = selectMode, onClick = { onSelectModeChange(!selectMode) }, label = { Text(Loc.str(R.string.timeline_select_mode)) }, colors = toggleChipColors())
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = showBeats, onClick = { onShowBeatsChange(!showBeats) }, label = { Text(Loc.str(R.string.efx_tablet_editor_screen_27)) }, colors = toggleChipColors())
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = snapToBeats, onClick = { onSnapChange(!snapToBeats) }, label = { Text(Loc.str(R.string.efx_tablet_editor_screen_28)) }, colors = toggleChipColors())
        Spacer(Modifier.width(8.dp))
        FilterChip(selected = followPlayhead, onClick = { onFollowChange(!followPlayhead) }, label = { Text(Loc.str(R.string.efx_tablet_editor_screen_29)) }, colors = toggleChipColors())
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, contentDescription = Loc.str(R.string.efx_tablet_editor_screen_30)) }
        IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, contentDescription = Loc.str(R.string.efx_tablet_editor_screen_31)) }
        TextButton(onClick = onFit) { Text(Loc.str(R.string.efx_tablet_editor_screen_32)) }
    }
}

/** 엔진이 고른 팔레트 색 계열 이름을 화면에 쓸 이름으로 바꾼다 */
private fun familyLabel(family: String): String = when (family) {
    "WARM" -> Loc.str(R.string.palette_family_warm)
    "COLD" -> Loc.str(R.string.palette_family_cool)
    "PURPLE" -> Loc.str(R.string.palette_family_purple)
    "RED" -> Loc.str(R.string.palette_family_red)
    "ORANGE_GOLD" -> Loc.str(R.string.palette_family_orange_gold)
    "TEAL" -> Loc.str(R.string.palette_family_teal)
    "BLUE" -> Loc.str(R.string.palette_family_blue)
    else -> family
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
            // 장르는 상단 제목 옆에 표시하므로 여기서는 현재 구간만 보여 준다 (비어 있어도 줄 높이는 유지)
            Text(
                text = section?.type ?: " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))

        // 오른쪽: 컨트롤 패널
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(Loc.str(R.string.label_preview), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                if (deviceConnected) {
                    Text(Loc.str(R.string.efx_tablet_editor_screen_33), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
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
                    Loc.str(R.string.efx_tablet_editor_screen_34),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 지속시간 표시: 0 이면 "다음 이펙트까지", 아니면 "4 비트 (1.63s)" (비트는 곡 분석이 있을 때만, 없으면 "1.63s") */
private fun formatDuration(entry: EfxEntry, beatTimesMs: List<Long>): String {
    val d = entry.payload.duration
    if (d == 0) return Loc.str(R.string.duration_until_next)
    val ms = com.efxcreator.model.EfxDurations.ticksToMs(d)
    val time = com.efxcreator.model.EfxDurations.formatMs(ms)
    if (beatTimesMs.size < 2) return time
    val beats = BeatGrid.beatsBetween(beatTimesMs, entry.timestampMs.toDouble(), (entry.timestampMs + ms).toDouble())
    val beatsText = BeatGrid.formatBeats(beats)
    return "$beatsText ${Loc.str(R.string.dlg_beat_unit)} ($time)"
}

/** 이펙트 한 개의 상세 항목 (시작 시간, 지속 시간, 종류, 색, 주기, SPF, 밝기, 그룹 마스크 …). 한 개 선택과 다중 선택의 펼침에서 함께 쓴다. */
@Composable
private fun EntryDetailRows(entry: EfxEntry, modifier: Modifier = Modifier, beatTimesMs: List<Long> = emptyList()) {
    val p = entry.payload
        val rows: List<Triple<String, String, Color?>> = listOf(
            Triple(Loc.str(R.string.efx_tablet_editor_screen_37), TimelineMath.formatTime(entry.timestampMs, withTenths = true), null),
            // 지속시간: 시작 시간 바로 아래. 곡 분석이 있으면 비트 수도 함께 보여 준다
            Triple(Loc.str(R.string.label_duration), formatDuration(entry, beatTimesMs), null),
            Triple(Loc.str(R.string.efx_tablet_editor_screen_38), p.effectType.name, null),
            Triple(Loc.str(R.string.efx_tablet_editor_screen_39), "RGB(${p.color.r}, ${p.color.g}, ${p.color.b})", Color(p.color.r, p.color.g, p.color.b)),
            Triple(
                Loc.str(R.string.efx_tablet_editor_screen_40),
                "RGB(${p.backgroundColor.r}, ${p.backgroundColor.g}, ${p.backgroundColor.b})",
                Color(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b)
            ),
            // 같은 패킷 필드: ON/OFF 는 Transit, BLINK/STROBE/BREATH 는 Period. 숫자는 프레임 수이고 1프레임 길이는 SPF 가 정한다
            Triple(TimelineMath.periodLabel(p.effectType), TimelineMath.formatFrames(p.period, p.spf), null),
            Triple("SPF", "${p.spf}  (${TimelineMath.frameUnitLabel(p.spf).substringBefore(" (")})", null),
            Triple(Loc.str(R.string.efx_tablet_editor_screen_41), TimelineMath.formatFade(p.fade), null),
            Triple(Loc.str(R.string.group_mask_picker_1), TimelineMath.formatGroupMask(p.groupMask), null),
            Triple(
                Loc.str(R.string.label_random_delay),
                if (p.randomDelay == 0) Loc.str(R.string.efx_tablet_editor_screen_42) else Loc.str(R.string.efx_tablet_editor_screen_43) + TimelineMath.formatFrames(p.randomDelay, p.spf),
                null
            ),
            Triple(Loc.str(R.string.label_random_color), if (p.randomColor != 0) "ON" else "OFF", null)
            // Effect Index 는 255 를 넘으면 0 부터 다시 시작하는 패킷 값이라 사용자에게는 의미가 없어 표시하지 않는다
        )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
                        color = if (k == Loc.str(R.string.label_random_color) && p.randomColor != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
    }
}

@Composable
private fun SelectedEntryPanel(
    modifier: Modifier = Modifier,
    entry: EfxEntry?,
    multiEntries: List<EfxEntry> = emptyList(),
    selectMode: Boolean = false,
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    beatTimesMs: List<Long> = emptyList()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(12.dp)
    ) {
        Text(Loc.str(R.string.efx_tablet_editor_screen_35), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        val multiCount = multiEntries.size
        if (multiCount > 0) {
            // 선택 모드: 선택 수, 구간, 이펙트별 한 줄 요약(펼치면 상세), 일괄 동작
            Text(Loc.str(R.string.multi_selected_count, multiCount), style = MaterialTheme.typography.titleMedium)
            val sorted = multiEntries.sortedBy { it.timestampMs }
            Text(
                Loc.str(
                    R.string.multi_range,
                    TimelineMath.formatTime(sorted.first().timestampMs, withTenths = true),
                    TimelineMath.formatTime(sorted.last().timestampMs, withTenths = true)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            // 이펙트별 한 줄 요약. 오른쪽 화살표로 상세 정보를 펼치고 접는다
            var expanded by remember { mutableStateOf<Set<Long>>(emptySet()) }
            val listScroll = rememberScrollState()
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(end = 10.dp).verticalScroll(listScroll),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sorted.forEach { e ->
                        val open = e.timestampMs in expanded
                        val p = e.payload
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                                .clickable { expanded = if (open) expanded - e.timestampMs else expanded + e.timestampMs }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    TimelineMath.formatTime(e.timestampMs, withTenths = true),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(62.dp)
                                )
                                Text(p.effectType.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                listOf(
                                    Color(p.color.r, p.color.g, p.color.b),
                                    Color(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b)
                                ).forEach { c ->
                                    Box(
                                        Modifier
                                            .padding(end = 4.dp)
                                            .size(14.dp)
                                            .background(c, MaterialTheme.shapes.extraSmall)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                                    )
                                }
                                Icon(
                                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (open) {
                                Spacer(Modifier.height(8.dp))
                                EntryDetailRows(e, beatTimesMs = beatTimesMs)
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
                SlimVerticalScrollBar(listScroll, Modifier.align(Alignment.CenterEnd))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClearSelection) { Text(Loc.str(R.string.multi_clear)) }
                Button(
                    onClick = onDeleteSelected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Loc.str(R.string.multi_delete_n, multiCount))
                }
            }
            return@Column
        }
        if (selectMode) {
            // 선택 모드인데 아직 고른 이펙트가 없을 때의 안내
            Text(
                Loc.str(R.string.multi_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onClearSelection) { Text(Loc.str(R.string.multi_exit)) }
            return@Column
        }
        if (entry == null) {
            Text(
                Loc.str(R.string.efx_tablet_editor_screen_36),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        // 항목이 많아 카드 높이를 넘으면 이 목록만 스크롤되고, 편집/삭제 버튼은 카드 하단에 고정된다
        val rowsScroll = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize().padding(end = 10.dp).verticalScroll(rowsScroll)) {
            EntryDetailRows(entry, beatTimesMs = beatTimesMs)
        }
        SlimVerticalScrollBar(rowsScroll, Modifier.align(Alignment.CenterEnd))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(Loc.str(R.string.efx_edit_screen_8))
            }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(Loc.str(R.string.efx_edit_screen_15))
            }
        }
    }
}
