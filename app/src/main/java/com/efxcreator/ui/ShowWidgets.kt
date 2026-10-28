package com.efxcreator.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.efxcreator.R
import com.efxcreator.model.Show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.efxcreator.core.Loc

/** "2025.03.14 (금) 19:30" */
fun formatShowDate(ms: Long): String =
    SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.getDefault()).format(Date(ms))

/** [maxPx] 정도 크기로 줄여서 읽는다 (목록용 썸네일이라 원본 해상도가 필요 없다) */
private fun decodeSampled(open: () -> InputStream?, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return open()?.use { BitmapFactory.decodeStream(it, null, opts) }
}

private fun loadBitmap(context: Context, path: String?, uri: Uri?, maxPx: Int): ImageBitmap? = try {
    when {
        uri != null -> decodeSampled({ context.contentResolver.openInputStream(uri) }, maxPx)
        path != null -> decodeSampled({ java.io.File(path).takeIf { it.exists() }?.inputStream() }, maxPx)
        else -> null
    }?.asImageBitmap()
} catch (e: Exception) {
    null
}

/**
 * 공연 이미지. 지정한 이미지가 없으면(또는 읽을 수 없으면) GLOWSYNC 기본 이미지를 보여 준다.
 * [pickedUri] 는 편집 중에 막 고른 이미지를 미리 보여 줄 때 쓴다.
 */
@Composable
fun ShowImage(
    imagePath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    pickedUri: Uri? = null
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imagePath, pickedUri) {
        value = withContext(Dispatchers.IO) { loadBitmap(context, imagePath, pickedUri, 512) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = Loc.str(R.string.show_widgets_1), contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            // 기본 이미지: 검은 바탕에 GLOWSYNC 로고
            Image(
                painterResource(R.drawable.logo_glowsync),
                contentDescription = "GLOWSYNC",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).padding(size / 8)
            )
        }
    }
}

/**
 * 공연 추가/수정 다이얼로그: 이름, 공연 일시, 공연 이미지.
 * 이미지를 지정하지 않으면 GLOWSYNC 기본 이미지를 쓴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowEditDialog(
    title: String,
    initial: Show?,
    confirmLabel: String,
    onConfirm: (name: String, performanceAt: Long?, newImage: Uri?, removeImage: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var performanceAt by remember { mutableStateOf(initial?.performanceAt) }
    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    var removeImage by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // 날짜를 고른 뒤 시간 선택 단계에서 쓰는 임시 값 (년/월/일)
    var pendingDate by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedImage = uri
            removeImage = false
        }
    }

    val hasCustomImage = pickedImage != null || (initial?.imagePath != null && !removeImage)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Loc.str(R.string.show_widgets_2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 공연 일시
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Loc.str(R.string.show_widgets_3), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(performanceAt?.let { formatShowDate(it) } ?: Loc.str(R.string.show_widgets_4))
                        }
                        if (performanceAt != null) {
                            TextButton(onClick = { performanceAt = null }) { Text(Loc.str(R.string.show_widgets_5)) }
                        }
                    }
                }

                // 공연 이미지
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Loc.str(R.string.show_widgets_1), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShowImage(
                            imagePath = if (removeImage) null else initial?.imagePath,
                            pickedUri = pickedImage,
                            size = 88.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (hasCustomImage) Loc.str(R.string.show_widgets_6) else Loc.str(R.string.show_widgets_7))
                            }
                            if (hasCustomImage) {
                                TextButton(onClick = { pickedImage = null; removeImage = true }) { Text(Loc.str(R.string.show_widgets_8)) }
                            }
                            Text(
                                Loc.str(R.string.show_widgets_9),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, performanceAt, pickedImage, removeImage && pickedImage == null) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
    )

    if (showDatePicker) {
        // DatePicker 는 UTC 자정 기준 ms 를 돌려준다
        val base = performanceAt ?: System.currentTimeMillis()
        val utcInitial = Calendar.getInstance().apply { timeInMillis = base }.let {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear(); set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = utcInitial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = dateState.selectedDateMillis != null,
                    onClick = {
                        dateState.selectedDateMillis?.let { ms ->
                            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                            pendingDate = Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                        }
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) { Text(Loc.str(R.string.show_widgets_10)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val cur = Calendar.getInstance().apply { timeInMillis = performanceAt ?: System.currentTimeMillis() }
        val timeState = rememberTimePickerState(
            initialHour = if (performanceAt != null) cur.get(Calendar.HOUR_OF_DAY) else 19,
            initialMinute = if (performanceAt != null) cur.get(Calendar.MINUTE) else 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(Loc.str(R.string.show_widgets_11)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDate?.let { (y, m, d) ->
                        performanceAt = Calendar.getInstance().apply {
                            clear(); set(y, m, d, timeState.hour, timeState.minute)
                        }.timeInMillis
                    }
                    showTimePicker = false
                }) { Text(Loc.str(R.string.efx_edit_screen_19)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
        )
    }
}

/** 앱 런처 아이콘 (적응형 아이콘이라 painterResource 대신 Drawable 을 비트맵으로 그린다) */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val bitmap = remember(px) {
        androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?.let { androidx.core.graphics.drawable.DrawableCompat.wrap(it) }
            ?.toBitmap(px, px)
            ?.asImageBitmap()
    }
    Box(modifier = modifier.size(size).clip(MaterialTheme.shapes.small)) {
        bitmap?.let { Image(it, contentDescription = "GLOWSYNC", modifier = Modifier.size(size)) }
    }
}

/**
 * 곡 이미지: 음악 파일에 들어 있는 앨범 아트(메타데이터)를 보여 준다.
 * 이미지가 없거나 파일을 읽을 수 없으면 음표 아이콘을 보여 준다.
 */
@Composable
fun SongArt(musicUriString: String?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, musicUriString) {
        value = withContext(Dispatchers.IO) { loadAlbumArt(musicUriString, 256) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = Loc.str(R.string.show_widgets_12), contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

private fun loadAlbumArt(musicUriString: String?, maxPx: Int): ImageBitmap? {
    val path = musicUriString?.let { Uri.parse(it).path } ?: return null
    val file = java.io.File(path)
    if (!file.exists()) return null
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val bytes = retriever.embeddedPicture ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
