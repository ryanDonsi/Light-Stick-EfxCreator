package com.efxcreator.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.ui.device.DeviceSettingsContent
import com.efxcreator.ui.device.activityBleViewModel
import com.efxcreator.ui.editor.SlimVerticalScrollBar
import com.efxcreator.ui.sets.EffectPatternsPanel
import com.efxcreator.ui.sets.EffectSetsPanel
import com.efxcreator.ui.sets.PaletteSetsPanel
import com.efxcreator.viewmodel.SettingsViewModel
import com.efxcreator.R
import com.efxcreator.core.Loc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    initialSection: String? = null,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    val efxStorageInfo by viewModel.efxStorageInfo.collectAsState()
    val musicLoadInfo by viewModel.musicLoadInfo.collectAsState()

    // EFX 폴더 선택 런처
    val efxFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        android.util.Log.d("FolderPicker", "efx result uri=$uri")
        uri?.let {
            try {
                // 지속적 권한 요청
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                viewModel.changeEfxStoragePathFromUri(it)
                Toast.makeText(
                    context,
                    Loc.str(R.string.settings_screen_1),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    Loc.str(R.string.settings_screen_2),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 음악 폴더 선택 런처
    val musicFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        android.util.Log.d("FolderPicker", "music result uri=$uri")
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                viewModel.changeMusicLoadPathFromUri(it)
                Toast.makeText(
                    context,
                    Loc.str(R.string.settings_screen_3),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    Loc.str(R.string.settings_screen_2),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 폴더 선택기를 루트가 아닌 Music 폴더에서 시작시킨다 (루트는 시스템이 막아 "이 폴더를 사용할 수 없음" 이 나옴)
    fun pickerStartUri(tag: String): Uri {
        val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music")
        android.util.Log.d("FolderPicker", "launch($tag) initial=$uri sdk=${android.os.Build.VERSION.SDK_INT} model=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        return uri
    }

    // ── 폴더 선택기 없이 GlowSync 폴더 바로 쓰기: "모든 파일 접근"을 허용받은 뒤 폴더를 만들어 적용한다 ──
    var pendingFolderPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun hasAllFilesAccess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager()
        else androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    fun applyFolder(dir: File) {
        val ok = viewModel.changeEfxStoragePathToFolder(dir)
        Toast.makeText(
            context,
            if (ok) Loc.str(R.string.settings_screen_1) else Loc.str(R.string.settings_folder_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val dir = pendingFolderPath?.let { File(it) }
        pendingFolderPath = null
        if (dir != null) {
            if (hasAllFilesAccess()) applyFolder(dir)
            else Toast.makeText(context, Loc.str(R.string.settings_folder_perm_denied), Toast.LENGTH_SHORT).show()
        }
    }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val dir = pendingFolderPath?.let { File(it) }
        pendingFolderPath = null
        if (dir != null) {
            if (granted) applyFolder(dir)
            else Toast.makeText(context, Loc.str(R.string.settings_folder_perm_denied), Toast.LENGTH_SHORT).show()
        }
    }
    fun requestFolder(dir: File) {
        if (hasAllFilesAccess()) {
            applyFolder(dir)
            return
        }
        pendingFolderPath = dir.absolutePath
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            try {
                allFilesLauncher.launch(intent)
            } catch (e: Exception) {
                allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    var section by rememberSaveable { mutableStateOf(SettingsSection.from(initialSection)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Loc.str(R.string.efx_list_screen_19)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Loc.str(R.string.efx_edit_screen_2))
                    }
                }
            )
        }
    ) { padding ->
        // 메인 화면과 같은 비율(0.36 : 0.64)로 왼쪽 메뉴 / 오른쪽 내용을 나눈다
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.weight(0.36f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Loc.str(R.string.settings_screen_4), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    // 메뉴 항목이 많아 화면 높이를 넘으면 스크롤된다
                    val menuScroll = rememberScrollState()
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(menuScroll),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsSection.entries.forEach { item ->
                                SettingsMenuRow(
                                    item = item,
                                    selected = item == section,
                                    onClick = { section = item }
                                )
                            }
                        }
                        SlimVerticalScrollBar(menuScroll, Modifier.align(Alignment.CenterEnd))
                    }
                }
            }

            Surface(
                modifier = Modifier.weight(0.64f).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    when (section) {
                        SettingsSection.STORAGE -> {
                            val scroll = rememberScrollState()
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(Loc.str(R.string.settings_screen_5), style = MaterialTheme.typography.titleMedium)
                                }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(end = 14.dp)
                                            .verticalScroll(scroll),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
            // EFX 저장 위치 섹션
            Text(
                text = Loc.str(R.string.settings_screen_6),
                style = MaterialTheme.typography.titleMedium
            )

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
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 현재 위치 + 오른쪽에 폴더 선택 아이콘 버튼
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Loc.str(R.string.settings_screen_7),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = efxStorageInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        FilledTonalIconButton(onClick = { efxFolderPicker.launch(pickerStartUri("efx")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = Loc.str(R.string.settings_screen_8))
                        }
                    }
                    // 폴더 선택기가 막힌 기기용: 선택기 없이 GlowSync 폴더를 바로 만들어 쓴다
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Loc.str(R.string.settings_folder_quick_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            requestFolder(File(android.os.Environment.getExternalStorageDirectory(), "GlowSync"))
                        }) { Text(Loc.str(R.string.settings_folder_root)) }
                        OutlinedButton(onClick = {
                            requestFolder(
                                File(
                                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                                    "GlowSync"
                                )
                            )
                        }) { Text(Loc.str(R.string.settings_folder_music)) }
                        TextButton(onClick = {
                            viewModel.resetEfxStoragePath()
                            Toast.makeText(context, Loc.str(R.string.settings_screen_1), Toast.LENGTH_SHORT).show()
                        }) { Text(Loc.str(R.string.settings_folder_reset)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 음악 불러오기 위치 섹션
            Text(
                text = Loc.str(R.string.settings_screen_9),
                style = MaterialTheme.typography.titleMedium
            )

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
                    modifier = Modifier.padding(16.dp)
                ) {
                    // 현재 위치 + 오른쪽에 폴더 선택 아이콘 버튼
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Loc.str(R.string.settings_screen_10),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = musicLoadInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        FilledTonalIconButton(onClick = { musicFolderPicker.launch(pickerStartUri("music")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = Loc.str(R.string.settings_screen_8))
                        }
                    }
                    // 폴더 선택기가 막힌 기기용: 선택기 없이 시작 위치를 바로 지정한다 (곡 추가 때 파일 선택기가 열리는 위치. 권한 불필요)
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = Loc.str(R.string.settings_music_quick_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            viewModel.setMusicLoadPath("default_music")
                            Toast.makeText(context, Loc.str(R.string.settings_screen_3), Toast.LENGTH_SHORT).show()
                        }) { Text(Loc.str(R.string.settings_music_default)) }
                        OutlinedButton(onClick = {
                            viewModel.setMusicLoadPath(File(android.os.Environment.getExternalStorageDirectory(), "GlowSync").absolutePath)
                            Toast.makeText(context, Loc.str(R.string.settings_screen_3), Toast.LENGTH_SHORT).show()
                        }) { Text(Loc.str(R.string.settings_folder_root)) }
                        OutlinedButton(onClick = {
                            viewModel.setMusicLoadPath(
                                File(
                                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                                    "GlowSync"
                                ).absolutePath
                            )
                            Toast.makeText(context, Loc.str(R.string.settings_screen_3), Toast.LENGTH_SHORT).show()
                        }) { Text(Loc.str(R.string.settings_folder_music)) }
                    }
                }
            }

            // 정보 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = Loc.str(R.string.settings_screen_11),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = Loc.str(R.string.settings_screen_12),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

                                    }
                                    SlimVerticalScrollBar(scroll, Modifier.align(Alignment.CenterEnd))
                                }
                            }
                        }
                        SettingsSection.DEVICE -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(Loc.str(R.string.settings_screen_13), style = MaterialTheme.typography.titleMedium)
                                }
                                DeviceSettingsContent(activityBleViewModel(), Modifier.fillMaxSize())
                            }
                        }
                        SettingsSection.EFFECT_LIBRARY -> EffectPatternsPanel(modifier = Modifier.fillMaxSize())
                        SettingsSection.EFFECT_SETS -> EffectSetsPanel(modifier = Modifier.fillMaxSize())
                        SettingsSection.PALETTE_SETS -> PaletteSetsPanel(modifier = Modifier.fillMaxSize())
                        SettingsSection.GAME -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(Loc.str(R.string.game_title), style = MaterialTheme.typography.titleMedium)
                                }
                                GameSettingsContent(viewModel(), Modifier.fillMaxSize())
                            }
                        }
                        SettingsSection.LANGUAGE -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(Loc.str(R.string.settings_screen_14), style = MaterialTheme.typography.titleMedium)
                                }
                                LanguageSettingsContent(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 설정 화면의 왼쪽 메뉴 항목 */
enum class SettingsSection(val route: String, private val labelRes: Int, private val subtitleRes: Int, val icon: ImageVector) {
    STORAGE("storage", R.string.settings_screen_5, R.string.settings_screen_15, Icons.Default.FolderOpen),
    DEVICE("device", R.string.settings_screen_13, R.string.settings_screen_16, Icons.Default.Bluetooth),
    EFFECT_LIBRARY("library", R.string.settings_screen_17, R.string.settings_screen_18, Icons.Default.Layers),
    EFFECT_SETS("effect", R.string.settings_screen_19, R.string.settings_screen_20, Icons.Default.AutoAwesome),
    PALETTE_SETS("palette", R.string.settings_screen_21, R.string.settings_screen_22, Icons.Default.Palette),
    GAME("game", R.string.game_title, R.string.game_subtitle, Icons.Default.SportsEsports),
    LANGUAGE("language", R.string.settings_screen_14, R.string.settings_screen_23, Icons.Default.Language);

    // 언어를 바꾸면 값이 달라지므로 enum 생성 시점이 아니라 읽을 때마다 문자열을 가져온다
    val label: String get() = Loc.str(labelRes)
    val subtitle: String get() = Loc.str(subtitleRes)

    companion object {
        fun from(route: String?): SettingsSection = entries.firstOrNull { it.route == route } ?: STORAGE
    }
}

@Composable
private fun SettingsMenuRow(item: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val sub = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.icon, contentDescription = null, tint = content)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(item.label, style = MaterialTheme.typography.titleSmall, color = content)
                Text(item.subtitle, style = MaterialTheme.typography.labelMedium, color = sub)
            }
        }
    }
}
