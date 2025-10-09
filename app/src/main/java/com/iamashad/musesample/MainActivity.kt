package com.iamashad.musesample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.iamashad.musesample.print.TestPcgScreen
import com.iamashad.musesample.screens.session.SessionListScreen
import com.iamashad.musesample.screens.session.SessionListViewModel
import com.iamashad.musesample.ui.theme.MuseSampleTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MuseSampleTheme {
                //val viewModel = ViewModel()
                //HomeScreen(viewModel)
                TestPcgScreen()
                //val viewModel = SessionListViewModel()
                //SessionListScreen(viewModel)
            }
        }
    }
}