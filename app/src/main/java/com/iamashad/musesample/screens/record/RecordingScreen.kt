package com.iamashad.musesample.screens.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RecordingScreen(
    vm: RecordingViewModel,
    onStopAndSave: () -> Unit,
    onCancel: () -> Unit
) {
    val state by vm.state.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (state == RecordingState.Recording) "Recording…" else "Ready",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (state == RecordingState.Recording) {
                    vm.toggleRecording()
                    onStopAndSave()
                } else vm.toggleRecording()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state == RecordingState.Recording) Color.Red else Color.Green
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state == RecordingState.Recording) "Stop Recording" else "Start Recording")
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Cancel")
        }
    }
}
