package com.iamashad.musesample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iamashad.musesample.navigation.AppNavigation
import com.iamashad.musesample.print.TestPcgScreen
import com.iamashad.musesample.screens.home.HomeViewModel
import com.iamashad.musesample.screens.metadata.MetadataViewModel
import com.iamashad.musesample.screens.record.RecordingViewModel
import com.iamashad.musesample.ui.theme.MuseSampleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MuseSampleTheme {
                // Activity-scoped VMs. Each screen pulls the instance from here,
                // keeping cross-screen state (e.g., device connection, recording options).
                val homeVM: HomeViewModel = viewModel()
                val recordingVM: RecordingViewModel = viewModel()
                val metadataVM: MetadataViewModel = viewModel()

                // Central NavHost (HOME → RECORD → META → SESSIONS).
                AppNavigation(homeVM, recordingVM, metadataVM)
                // For manual testing of PDF generation UI, you can show:
                //TestPcgScreen()
            }
        }
    }
}
