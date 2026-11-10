package com.efxcreator

import android.content.Context
import android.os.Bundle
import com.efxcreator.core.LocaleHelper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.efxcreator.ui.theme.EfxCreatorTheme
import com.efxcreator.viewmodel.BleViewModel
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.efxcreator.core.Loc
import com.efxcreator.data.PreferencesManager

class MainActivity : ComponentActivity() {

    private val bleVm: BleViewModel by viewModels()

    // 자동 연결 기기가 있는데 BLE 권한이 없으면 시작 시 요청하고, 허용되면 바로 자동 연결한다
    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) bleVm.tryAutoConnect()
        }

    // 기본 저장 위치(Music/Effects)에 필요한 "모든 파일 접근" 안내 팝업. 한 번만 보여 준다
    private var showStoragePrompt by mutableStateOf(false)

    // 권한 화면에서 돌아오면 허용됐을 때 기본 폴더를 만든다
    private val allFilesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            PreferencesManager.ensureDefaultEfxDir(this)
        }
    private val legacyStorageLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            PreferencesManager.ensureDefaultEfxDir(this)
        }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                allFilesLauncher.launch(intent)
            } catch (e: Exception) {
                allFilesLauncher.launch(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyStorageLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면 등에서 권한을 허용하고 돌아온 경우에도 기본 폴더를 만들어 둔다
        PreferencesManager.ensureDefaultEfxDir(this)
    }

    // 저장된 표시 언어를 이 화면에도 적용한다
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (bleVm.autoConnectDevices.value.isNotEmpty() && !bleVm.hasPermissions()) {
            blePermissionLauncher.launch(BleViewModel.requiredPermissions())
        }
        val prefs = PreferencesManager(this)
        if (PreferencesManager.isDefaultEfxPath(prefs.efxStoragePath) &&
            !PreferencesManager.canWriteFolders(this) && !prefs.storagePermissionPromptShown
        ) {
            showStoragePrompt = true
        }
        // 어두운 배경에서 상태바/내비게이션바 아이콘이 밝게 보이도록 고정
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            EfxCreatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EfxCreatorApp()
                    if (showStoragePrompt) {
                        AlertDialog(
                            onDismissRequest = {
                                prefs.storagePermissionPromptShown = true
                                showStoragePrompt = false
                            },
                            title = { Text(Loc.str(R.string.storage_perm_title)) },
                            text = { Text(Loc.str(R.string.storage_perm_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    prefs.storagePermissionPromptShown = true
                                    showStoragePrompt = false
                                    requestStoragePermission()
                                }) { Text(Loc.str(R.string.storage_perm_allow)) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    prefs.storagePermissionPromptShown = true
                                    showStoragePrompt = false
                                }) { Text(Loc.str(R.string.storage_perm_later)) }
                            }
                        )
                    }
                }
            }
        }
    }
}
