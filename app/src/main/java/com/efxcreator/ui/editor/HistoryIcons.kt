package com.efxcreator.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Material Symbols(Outlined, weight 400) 의 Undo / Redo 아이콘.
 * androidx 기본 아이콘 세트(Icons.Default.Undo)는 가로로 길어서, 말려 돌아오는 곡선 모양의 새 Symbols 경로를 직접 옮겼다.
 * 경로는 fonts.gstatic.com 의 공식 SVG(viewBox "0 -960 960 960")와 같다.
 */
object HistoryIcons {

    val Undo: ImageVector by lazy {
        symbol(
            "Undo",
            "M280-200v-80h284q63 0 109.5-40T720-420q0-60-46.5-100T564-560H312l104 104-56 56-200-200 200-200 " +
                "56 56-104 104h252q97 0 166.5 63T800-420q0 94-69.5 157T564-200H280Z"
        )
    }

    val Redo: ImageVector by lazy {
        symbol(
            "Redo",
            "M396-200q-97 0-166.5-63T160-420q0-94 69.5-157T396-640h252L544-744l56-56 200 200-200 200-56-56 " +
                "104-104H396q-63 0-109.5 40T240-420q0 60 46.5 100T396-280h284v80H396Z"
        )
    }

    /** viewBox 가 y = -960..0 이라 Compose 의 (0,0) 기준 뷰포트로 옮기려고 y 를 960 만큼 내린다 */
    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        )
            .addGroup(translationY = 960f)
            .addPath(PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
}
