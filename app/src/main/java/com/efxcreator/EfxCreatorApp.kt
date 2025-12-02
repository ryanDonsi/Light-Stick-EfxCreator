package com.efxcreator

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.efxcreator.ui.EfxEditScreen
import com.efxcreator.ui.EfxListScreen
import com.efxcreator.ui.SettingsScreen

sealed class Screen(val route: String) {
    object List : Screen("list")
    object Edit : Screen("edit/{projectId}") {
        fun createRoute(projectId: String) = "edit/$projectId"
    }
    object Settings : Screen("settings")  // ← 추가
}

@Composable
fun EfxCreatorApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.List.route
    ) {
        composable(Screen.List.route) {
            EfxListScreen(
                onNavigateToEdit = { projectId ->
                    navController.navigate(Screen.Edit.createRoute(projectId))
                },
                onNavigateToSettings = {  // ← 추가
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Edit.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            EfxEditScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ← 설정 화면 추가
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}