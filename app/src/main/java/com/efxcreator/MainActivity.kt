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
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.efxcreator.data.EfxRepository
import com.efxcreator.data.PreferencesManager

class MainActivity : ComponentActivity() {

    private val bleVm: BleViewModel by viewModels()

    // 자동 연결 기기가 있는데 BLE 권한이 없으면 시작 시 요청하고, 허용되면 바로 자동 연결한다
    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) bleVm.tryAutoConnect()
        }

    // EFX 저장 폴더 안내 팝업 (Light-Stick-Music 과 같은 방식): 폴더가 지정될 때까지 앱을 열 때마다 보여 준다
    private var showFolderGuide by mutableStateOf(false)

    // 폴더 선택기: Music/Effects 에서 시작해 사용자가 고른 폴더에 읽기/쓰기 접근권을 영구 저장한다
    private val efxFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                if (DocumentFile.fromTreeUri(this, uri)?.canWrite() == true) {
                    EfxRepository(this).changeEfxStoragePath(uri.toString())
                    Toast.makeText(this, Loc.str(R.string.settings_screen_1), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, Loc.str(R.string.settings_screen_2), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, Loc.str(R.string.settings_screen_2), Toast.LENGTH_SHORT).show()
            }
        }

    private fun requestEfxFolder() {
        val start = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music/Effects")
        efxFolderPicker.launch(start)
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
        if (PreferencesManager.isDefaultEfxPath(prefs.efxStoragePath)) showFolderGuide = true
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
                    if (showFolderGuide) {
                        AlertDialog(
                            onDismissRequest = { showFolderGuide = false },
                            title = { Text(Loc.str(R.string.folder_guide_title)) },
                            text = { Text(Loc.str(R.string.folder_guide_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showFolderGuide = false
                                    requestEfxFolder()
                                }) { Text(Loc.str(R.string.folder_guide_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showFolderGuide = false }) {
                                    Text(Loc.str(R.string.folder_guide_later))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
