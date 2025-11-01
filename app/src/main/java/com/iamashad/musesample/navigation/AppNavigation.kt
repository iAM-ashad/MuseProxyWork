package com.iamashad.musesample.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iamashad.musesample.screens.home.HomeScreen
import com.iamashad.musesample.screens.home.HomeViewModel
import com.iamashad.musesample.screens.metadata.MetadataScreen
import com.iamashad.musesample.screens.metadata.MetadataViewModel
import com.iamashad.musesample.screens.record.RecordingScreen
import com.iamashad.musesample.screens.record.RecordingViewModel
import com.iamashad.musesample.screens.session.SessionListScreen

/**
 * App-level navigation graph with lightweight cross-fade transitions.
 *
 * Destinations:
 *  - HOME → device connection + entry point.
 *  - RECORD → live capture UI.
 *  - META → form to save session metadata (accepts optional wav path).
 *  - SESSIONS → session history list.
 *
 * ViewModels are owned by the caller (activity) and passed down so screens
 * can share state across destinations (e.g., Recording → Metadata).
 */
@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    recordingViewModel: RecordingViewModel,
    metadataViewModel: MetadataViewModel
) {
    val nav: NavHostController = rememberNavController()

    // enter exit fade animations
    val enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 90))
    val exit = fadeOut(animationSpec = tween(durationMillis = 90))

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit }
    ) {
        composable(Routes.HOME) {
            HomeScreen(nav, homeViewModel)
        }

        composable(Routes.RECORD) {
            RecordingScreen(
                vm = recordingViewModel,
                onStopAndSave = { wav ->
                    // Accept path from VM if callback path is null.
                    val safe = wav ?: recordingViewModel.lastWavPath()
                    if (!safe.isNullOrEmpty()) {
                        val encoded = Uri.encode(safe)
                        nav.navigate("${Routes.META}?wav=$encoded")
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }

        // Metadata route accepts a nullable WAV path.
        composable(
            route = "${Routes.META}?wav={wav}",
            arguments = listOf(
                navArgument("wav") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStack ->
            val wav = backStack.arguments?.getString("wav")?.let(Uri::decode).orEmpty()
            MetadataScreen(
                wavPath = wav,
                onSaved = { nav.navigate(Routes.SESSIONS) },
                vm = metadataViewModel
            )
        }

        composable(Routes.SESSIONS) {
            SessionListScreen(onBack = { nav.popBackStack() })
        }
    }
}
