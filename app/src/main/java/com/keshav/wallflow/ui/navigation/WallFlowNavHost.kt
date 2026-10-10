package com.keshav.wallflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keshav.wallflow.editor.EditorScreen
import com.keshav.wallflow.ui.screen.detail.DetailScreen
import com.keshav.wallflow.ui.screen.gallery.GalleryScreen
import com.keshav.wallflow.ui.screen.settings.RotationSettingsScreen

object Routes {
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{wallpaperId}"
    const val EDITOR = "editor/{wallpaperId}"
    const val ARG_WALLPAPER_ID = "wallpaperId"

    fun detail(id: Long) = "detail/$id"
    fun editor(id: Long) = "editor/$id"
}

@Composable
fun WallFlowNavHost(
    pendingEditorId: Long? = null,
    onPendingEditorConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // A shared image was imported: jump straight into the editor for it.
    LaunchedEffect(pendingEditorId) {
        if (pendingEditorId != null) {
            navController.navigate(Routes.editor(pendingEditorId))
            onPendingEditorConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.GALLERY) {
        composable(Routes.GALLERY) {
            GalleryScreen(
                onWallpaperClick = { id -> navController.navigate(Routes.detail(id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_WALLPAPER_ID) { type = NavType.LongType }),
        ) {
            DetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.editor(id)) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_WALLPAPER_ID) { type = NavType.LongType }),
        ) {
            EditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            RotationSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
