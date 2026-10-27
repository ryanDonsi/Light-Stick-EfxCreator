package com.efxcreator.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
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
import com.efxcreator.ui.editor.SlimVerticalScrollBar
import com.efxcreator.ui.sets.EffectSetsPanel
import com.efxcreator.ui.sets.PaletteSetsPanel
import com.efxcreator.viewmodel.SettingsViewModel

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
                    "EFX 저장 위치가 변경되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "폴더 권한을 얻을 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 음악 폴더 선택 런처
    val musicFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                viewModel.changeMusicLoadPathFromUri(it)
                Toast.makeText(
                    context,
                    "음악 불러오기 위치가 변경되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "폴더 권한을 얻을 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    var section by rememberSaveable { mutableStateOf(SettingsSection.from(initialSection)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
                        Text("설정 메뉴", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SettingsSection.entries.forEach { item ->
                            SettingsMenuRow(
                                item = item,
                                selected = item == section,
                                onClick = { section = item }
                            )
                        }
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
                                    Text("저장소 관리", style = MaterialTheme.typography.titleMedium)
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
                text = "EFX 파일 저장",
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
                    Text(
                        text = "현재 저장 위치",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = efxStorageInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { efxFolderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("폴더 선택")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 음악 불러오기 위치 섹션
            Text(
                text = "음악 파일 불러오기",
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
                    Text(
                        text = "현재 불러오기 위치",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = musicLoadInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { musicFolderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("폴더 선택")
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
                        text = "ℹ️ 폴더 설정 안내",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 폴더 선택 버튼을 눌러 원하는 폴더를 선택하세요\n" +
                                "• 선택한 폴더는 앱 재시작 후에도 유지됩니다\n" +
                                "• 폴더 권한은 자동으로 관리됩니다",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

                                    }
                                    SlimVerticalScrollBar(scroll, Modifier.align(Alignment.CenterEnd))
                                }
                            }
                        }
                        SettingsSection.EFFECT_SETS -> EffectSetsPanel(modifier = Modifier.fillMaxSize())
                        SettingsSection.PALETTE_SETS -> PaletteSetsPanel(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** 설정 화면의 왼쪽 메뉴 항목 */
enum class SettingsSection(val route: String, val label: String, val subtitle: String, val icon: ImageVector) {
    STORAGE("storage", "저장소 관리", "EFX 저장 위치 · 음악 불러오기 위치", Icons.Default.FolderOpen),
    EFFECT_SETS("effect", "이펙트 세트", "섹션별 이펙트 규칙", Icons.Default.AutoAwesome),
    PALETTE_SETS("palette", "팔레트 세트", "자동 생성에 쓰는 색 조합", Icons.Default.Palette);

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
