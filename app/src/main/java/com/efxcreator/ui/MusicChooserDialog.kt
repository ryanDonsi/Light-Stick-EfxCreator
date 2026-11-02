package com.efxcreator.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.efxcreator.R
import com.efxcreator.core.Loc
import com.efxcreator.ui.editor.TimelineMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 기기에 있는 음악 한 곡 (MediaStore) */
data class DeviceSong(
    val uri: Uri,
    val title: String,
    val artist: String,
    val folder: String,
    val durationMs: Long
)

private fun querySongs(context: Context): List<DeviceSong> {
    val result = mutableListOf<DeviceSong>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.RELATIVE_PATH
    )
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0",
        null,
        "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    )?.use { c ->
        val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val rel = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
        while (c.moveToNext()) {
            val artistName = c.getString(artist)?.takeIf { it != "<unknown>" }.orEmpty()
            result += DeviceSong(
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(id)),
                title = c.getString(title).orEmpty(),
                artist = artistName,
                folder = c.getString(rel).orEmpty().trimEnd('/'),
                durationMs = c.getLong(dur)
            )
        }
    }
    return result
}

/**
 * 곡 추가용 음악 선택창. 기기의 음악을 목록으로 보여 주고, 체크박스로 여러 곡을 한 번에 고른다.
 * (시스템 파일 선택기는 기기에 따라 한 곡만 돌려주므로 앱 안에서 직접 고른다.)
 */
@Composable
fun MusicChooserDialog(
    onConfirm: (List<Uri>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<DeviceSong>?>(null) }
    var query by remember { mutableStateOf("") }
    val checked = remember { mutableStateOf<Set<Uri>>(emptySet()) }

    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) { runCatching { querySongs(context) }.getOrDefault(emptyList()) }
    }

    val all = songs.orEmpty()
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
    }
    val selected = checked.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Loc.str(R.string.music_chooser_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(Loc.str(R.string.music_chooser_search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Loc.str(R.string.music_chooser_count, selected.size, all.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        // 지금 보이는 곡 전체 선택 (모두 이미 선택돼 있으면 보이는 곡 선택 해제)
                        val shownUris = shown.map { it.uri }
                        checked.value = if (selected.containsAll(shownUris)) selected - shownUris.toSet()
                        else selected + shownUris
                    }, enabled = shown.isNotEmpty()) { Text(Loc.str(R.string.music_chooser_toggle_all)) }
                }
                when {
                    songs == null -> Text(Loc.str(R.string.music_chooser_loading), style = MaterialTheme.typography.bodyMedium)
                    shown.isEmpty() -> Text(Loc.str(R.string.music_chooser_empty), style = MaterialTheme.typography.bodyMedium)
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(shown, key = { it.uri.toString() }) { song ->
                            val on = song.uri in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checked.value = if (on) selected - song.uri else selected + song.uri },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                    Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(song.artist, song.folder).filter { it.isNotEmpty() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    TimelineMath.formatTime(song.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // 목록 순서대로 넘긴다
            TextButton(
                onClick = { onConfirm(all.map { it.uri }.filter { it in selected }) },
                enabled = selected.isNotEmpty()
            ) { Text(Loc.str(R.string.music_chooser_confirm, selected.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Loc.str(R.string.music_chooser_cancel)) } }
    )
}
