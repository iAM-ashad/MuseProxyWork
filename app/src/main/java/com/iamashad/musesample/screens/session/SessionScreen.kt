package com.iamashad.musesample.screens.session

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iamashad.musesample.model.SessionItem
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenPdf: (File) -> Unit = {},
    onPlayAudio: (File) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session History") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val sessions = viewModel.sessions

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No sessions recorded yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(session, onOpenPdf, onPlayAudio)
                }
            }
        }
    }
}

@Composable
fun SessionCard(
    session: SessionItem,
    onOpenPdf: (File) -> Unit,
    onPlayAudio: (File) -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var player: MediaPlayer? by remember { mutableStateOf(null) }

    fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        isPlaying = false
    }

    fun startPlayback(file: File) {
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { stopPlayback() }
            }
            isPlaying = true
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Patient and session metadata
            Text(
                "${session.patientName} (ID: ${session.patientId})",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Recorded: ${session.dateTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Device: ${session.deviceModel}",
                style = MaterialTheme.typography.bodySmall
            )
            if (session.notes.isNotEmpty()) {
                Text(
                    "Notes: ${session.notes}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row (flexible layout for future expansion)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                session.pdfFile?.let {
                    OutlinedButton(
                        onClick = { onOpenPdf(it) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Report")
                    }
                }

                session.audioFile?.let {
                    Button(
                        onClick = {
                            if (isPlaying) stopPlayback() else startPlayback(it)
                            onPlayAudio(it)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPlaying) "Stop Audio" else "Play Audio")
                    }
                }
            }
        }
    }
}

/**
 * Dummy data to show in the list.
 */
fun demoSessions(): List<SessionItem> = listOf(
    SessionItem(
        id = "sess_001",
        patientName = "Jane Doe",
        patientId = "P-123456",
        dateTime = "06 Oct 2025, 10:15 AM",
        deviceModel = "TAAL Stethoscope",
        notes = "Baseline PCG recording",
        pdfFile = File("/storage/emulated/0/Documents/PCG_Report_1.pdf"),
        audioFile = File("/storage/emulated/0/Music/recording1.wav")
    ),
    SessionItem(
        id = "sess_002",
        patientName = "John Smith",
        patientId = "P-654321",
        dateTime = "05 Oct 2025, 02:40 PM",
        deviceModel = "TAAL ECG",
        notes = "Follow-up session",
        pdfFile = File("/storage/emulated/0/Documents/PCG_Report_2.pdf"),
        audioFile = File("/storage/emulated/0/Music/recording2.wav")
    )
)
