package com.iamashad.musesample.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    NavHost(navController = nav, startDestination = Routes.SESSIONS) {
        composable(Routes.HOME) {
            HomeScreen(
                nav,
                homeViewModel
            )
        }
        composable(Routes.RECORD) {
            RecordingScreen(
                vm = recordingViewModel,
                onStopAndSave = { wav ->
                    val safe = wav ?: recordingViewModel.lastWavPath()
                    if (!safe.isNullOrEmpty()) {
                        nav.navigate("${Routes.META}?wav=$safe")
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }
        composable("${Routes.META}?wav={wav}") { backStack ->
            val wav = backStack.arguments?.getString("wav") ?: ""
            MetadataScreen(
                wavPath = wav,
                onSaved = { nav.navigate(Routes.SESSIONS) },
                vm = metadataViewModel
            )
        }
        composable(Routes.SESSIONS) {
            SessionListScreen(
                onBack = { nav.popBackStack() }
            )
        }
    }
}
