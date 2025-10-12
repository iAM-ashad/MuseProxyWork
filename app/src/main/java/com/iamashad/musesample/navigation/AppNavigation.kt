package com.iamashad.musesample.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation(
    homeViewModel: HomeViewModel,
    recordingViewModel: RecordingViewModel,
    metadataViewModel: MetadataViewModel
) {
    val nav: NavHostController = rememberNavController()
    val ctx = LocalContext.current

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                nav,
                homeViewModel
            )
        }
        composable(Routes.RECORD) {
            RecordingScreen(
                vm = recordingViewModel,
                onStopAndSave = {
                    val wav = recordingViewModel.lastWavPath()
                    if (wav != null) nav.navigate("${Routes.META}?wav=$wav")
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