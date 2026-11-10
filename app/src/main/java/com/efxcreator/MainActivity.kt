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

class MainActivity : ComponentActivity() {

    private val bleVm: BleViewModel by viewModels()

    // 자동 연결 기기가 있는데 BLE 권한이 없으면 시작 시 요청하고, 허용되면 바로 자동 연결한다
    private val blePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) bleVm.tryAutoConnect()
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
                }
            }
        }
    }
}
