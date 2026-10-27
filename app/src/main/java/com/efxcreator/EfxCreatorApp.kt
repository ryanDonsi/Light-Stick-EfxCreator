package com.efxcreator

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import com.efxcreator.ui.theme.EfxMotion
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.efxcreator.ui.editor.EfxTabletEditorScreen
import com.efxcreator.ui.EfxListScreen
import com.efxcreator.ui.SettingsScreen

sealed class Screen(val route: String) {
    object List : Screen("list")
    object Edit : Screen("edit/{projectId}") {
        fun createRoute(projectId: String) = "edit/$projectId"
    }
    /** 설정: 저장소 관리 / 이펙트 세트 / 팔레트 세트. [section] 으로 처음 열 메뉴를 고른다. */
    object Settings : Screen("settings?section={section}") {
        fun createRoute(section: String = "storage") = "settings?section=$section"
    }
}

@Composable
fun EfxCreatorApp() {
    val navController = rememberNavController()

    // Material 3 모션 — Shared axis (X): 화면이 가로로 조금 밀리며 fade through 로 교체된다
    val slide = EfxMotion.SLIDE_FRACTION
    NavHost(
        navController = navController,
        startDestination = Screen.List.route,
        enterTransition = {
            slideInHorizontally(tween(EfxMotion.LONG, easing = EfxMotion.EmphasizedDecelerate)) { it / slide } +
                fadeIn(tween(210, delayMillis = 90, easing = EfxMotion.StandardDecelerate))
        },
        exitTransition = {
            slideOutHorizontally(tween(EfxMotion.MEDIUM, easing = EfxMotion.EmphasizedAccelerate)) { -it / slide } +
                fadeOut(tween(90, easing = EfxMotion.StandardAccelerate))
        },
        popEnterTransition = {
            slideInHorizontally(tween(EfxMotion.LONG, easing = EfxMotion.EmphasizedDecelerate)) { -it / slide } +
                fadeIn(tween(210, delayMillis = 90, easing = EfxMotion.StandardDecelerate))
        },
        popExitTransition = {
            slideOutHorizontally(tween(EfxMotion.MEDIUM, easing = EfxMotion.EmphasizedAccelerate)) { it / slide } +
                fadeOut(tween(90, easing = EfxMotion.StandardAccelerate))
        }
    ) {
        composable(Screen.List.route) {
            EfxListScreen(
                onNavigateToEdit = { projectId ->
                    navController.navigate(Screen.Edit.createRoute(projectId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.createRoute())
                },
                onNavigateToSets = { navController.navigate(Screen.Settings.createRoute("effect")) }
            )
        }

        composable(
            route = Screen.Edit.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            // 태블릿 전용 에디터 (파형/섹션 타임라인 + 드래그&드롭 + 재생/Preview)
            EfxTabletEditorScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSets = { navController.navigate(Screen.Settings.createRoute("effect")) }
            )
        }

        composable(
            route = Screen.Settings.route,
            arguments = listOf(
                navArgument("section") { type = NavType.StringType; defaultValue = "storage" }
            )
        ) { backStackEntry ->
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                initialSection = backStackEntry.arguments?.getString("section")
            )
        }
    }
}