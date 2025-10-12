package com.iamashad.musesample.screens.session

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.iamashad.musesample.generatePcgPdf
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.repository.SessionRepository
import kotlinx.coroutines.launch
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SessionListScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by SessionRepository.sessions.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sessions", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions) { s ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${s.patientName} (ID: ${s.patientId})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(s.sessionStart, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                // play audio
                                val uri = FileProvider.getUriForFile(
                                    ctx,
                                    "${ctx.packageName}.fileprovider",
                                    File(s.wavPath)
                                )
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "audio/wav")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            }) { Text("Play Audio") }

                            Button(onClick = {
                                scope.launch {
                                    val pdf = generatePcgPdf(
                                        context = ctx,
                                        wavPath = s.wavPath,
                                        meta = PcgReportMeta(
                                            patientName = s.patientName,
                                            patientId = s.patientId,
                                            sessionStart = s.sessionStart,
                                            deviceModel = s.deviceModel,
                                            notes = s.notes,
                                            age = "",          // optional fields for week 2
                                            sex = "S.E.X.: N/A",
                                            height = "",
                                            weight = "",
                                            bmi = "",
                                            posture = ""
                                        )
                                    )
                                    SessionRepository.updatePdf(s.id, pdf.absolutePath)

                                    val uri = FileProvider.getUriForFile(
                                        ctx,
                                        "${ctx.packageName}.fileprovider",
                                        pdf
                                    )
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                }
                            }) { Text("Generate PDF") }
                        }
                    }
                }
            }
        }
    }
}

