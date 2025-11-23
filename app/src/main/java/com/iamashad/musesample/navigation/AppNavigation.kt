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

@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    recordingViewModel: RecordingViewModel,
    metadataViewModel: MetadataViewModel
) {
    val nav: NavHostController = rememberNavController()

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
            HomeScreen(navController = nav, viewModel = homeViewModel)
        }

        composable(Routes.RECORD) {
            RecordingScreen(
                vm = recordingViewModel,
                onStopAndSave = { filteredPath, rawPath ->
                    // We *could* use the paths here for debugging, but the VM has
                    // already inserted a DB row. Just navigate to Sessions.
                    nav.navigate(Routes.SESSIONS) {
                        // Optional: clear intermediate back stack so Back goes home.
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }

        // Metadata route accepts a WAV path and raw WAV path
        composable(
            route = "${Routes.META}?wav={wav}&rawWav={rawWav}",
            arguments = listOf(
                navArgument("wav") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("rawWav") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val wav = backStackEntry.arguments
                ?.getString("wav")
                ?.let(Uri::decode)
                .orEmpty()

            val rawWav = backStackEntry.arguments
                ?.getString("rawWav")
                ?.let(Uri::decode)
                .orEmpty()

            MetadataScreen(
                wavPath = wav,
                rawWavPath = rawWav,
                onSaved = { nav.navigate(Routes.SESSIONS) },
                vm = metadataViewModel
            )
        }

        composable(Routes.SESSIONS) {
            SessionListScreen(
                onBack = { nav.navigate(Routes.HOME) },
                onEditMetadataAndGeneratePdf = { session ->
                    val encodedFiltered = Uri.encode(session.wavPath)
                    val encodedRaw = Uri.encode(session.rawWavPath)
                    nav.navigate("${Routes.META}?wav=$encodedFiltered&rawWav=$encodedRaw")
                }
            )
        }
    }
}
