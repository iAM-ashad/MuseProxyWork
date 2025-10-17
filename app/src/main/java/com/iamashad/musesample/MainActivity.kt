package com.iamashad.musesample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iamashad.musesample.navigation.AppNavigation
import com.iamashad.musesample.screens.home.HomeViewModel
import com.iamashad.musesample.screens.metadata.MetadataViewModel
import com.iamashad.musesample.screens.record.RecordingViewModel
import com.iamashad.musesample.ui.theme.MuseSampleTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseSampleTheme {
                val homeVM: HomeViewModel = viewModel()
                val recordingVM: RecordingViewModel = viewModel()
                val metadataVM: MetadataViewModel = viewModel()
                AppNavigation(homeVM, recordingVM, metadataVM)
            }
        }
    }
}