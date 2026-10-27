package com.efxcreator.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Activity 범위의 ViewModel. 화면(내비게이션 항목)마다 따로 만들어지는 ViewModel 과 달리 모든 화면이 같은 인스턴스를 써서,
 * 설정에서 바꾼 내용이 에디터에 바로 보인다.
 */
@Composable
inline fun <reified T : ViewModel> activityViewModel(): T {
    var ctx = LocalContext.current
    while (ctx is ContextWrapper && ctx !is ComponentActivity) ctx = ctx.baseContext
    return viewModel(viewModelStoreOwner = ctx as ComponentActivity)
}
