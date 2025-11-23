package com.iamashad.musesample.print

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.iamashad.musesample.R
import com.iamashad.musesample.generatePcgPdf
import com.iamashad.musesample.model.PcgReportMeta
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Developer sandbox screen to:
 * - Pick a .wav file from device storage,
 * - Treat it as the SDK-FILTERED WAV path for our current implementation,
 * - Generate a PCG PDF using [generatePcgPdf],
 * - Open the generated PDF via an external viewer.
 *
 * NOTE:
 * In production, `generatePcgPdf` is typically called with the TAAL SDK–filtered file path
 * stored in the Session. Here, we just let you pick any WAV and feed that path into the
 * same pipeline to exercise the rendering + segmentation.
 */
@Composable
fun TestPcgScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var wavPath by remember { mutableStateOf<String?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (isError, text)

    // Document picker for WAV files; copies selection into app-internal storage.
    val pickWav = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val copied = copyToInternal(ctx, uri)
            wavPath = copied?.absolutePath
            pdfFile = null
            message = if (copied != null) {
                false to "Loaded: ${copied.name}"
            } else {
                true to "Failed to copy the .wav file"
            }
        }
    }

    // Header logo
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp)
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.muse_logo),
            contentDescription = "Company Logo",
        )
    }

    // Controls + status
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "PCG Report Generator",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !busy,
                onClick = {
                    pickWav.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
                },
            ) {
                Text(if (wavPath == null) "Pick .wav" else "Another")
            }

            Button(
                enabled = wavPath != null && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            val pickedPath = wavPath
                            if (pickedPath == null) {
                                message = true to "No WAV file selected"
                            } else {
                                // For this test screen we treat the picked file as the
                                // SDK-FILTERED WAV path that generatePcgPdf expects.
                                val file = generatePcgPdf(
                                    context = ctx,
                                    rawWavPath = null,
                                    filteredWavPath = pickedPath,
                                    meta = PcgReportMeta(
                                        patientName = "Jane Doe",
                                        patientId = "P-123456",
                                        age = "24",
                                        sex = "Female",
                                        height = "160",
                                        weight = "60",
                                        bmi = "22.5",
                                        posture = "Standing",
                                        sessionStart = LocalDateTime.now()
                                            .format(
                                                DateTimeFormatter.ofPattern(
                                                    "dd MMM yyyy, HH:mm"
                                                )
                                            ),
                                        deviceModel = Build.MODEL ?: "Unknown",
                                        notes = "Dummy PCG capture"
                                    )
                                )

                                pdfFile = file
                                message = false to "Saved PDF: ${file.name}"
                            }
                        } catch (t: Throwable) {
                            message = true to (t.message ?: "Error generating PDF")
                            t.printStackTrace()
                        } finally {
                            busy = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                    disabledContainerColor = Color.DarkGray,
                    disabledContentColor = Color.White
                )
            ) {
                Text("Generate PDF")
            }

            OutlinedButton(
                enabled = pdfFile != null && !busy,
                onClick = { pdfFile?.let { openPdf(ctx, it) } },
            ) {
                Text("Open PDF")
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        message?.let { (isError, text) ->
            AssistChip(
                onClick = { message = null },
                label = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isError) {
                        Log.d("Html Logo", message!!.second)
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    labelColor = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            )
        }

        InfoCard(
            title = "Selected WAV",
            primary = wavPath?.substringAfterLast('/') ?: "(none)",
            secondary = wavPath ?: "Please choose a .wav file"
        )

        InfoCard(
            title = "Generated PDF",
            primary = pdfFile?.name ?: "(none)",
            secondary = pdfFile?.absolutePath ?: "Generate to create a PDF"
        )
    }
}

@Composable
private fun InfoCard(title: String, primary: String, secondary: String) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                primary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Copy a content-URI WAV to internal storage so we can work with a file path. */
private fun copyToInternal(context: Context, uri: Uri): File? = try {
    val name = "picked_${System.currentTimeMillis()}.wav"
    val out = File(context.filesDir, name)
    context.contentResolver.openInputStream(uri).use { ins ->
        FileOutputStream(out).use { outs -> ins?.copyTo(outs) }
    }
    out
} catch (t: Throwable) {
    null
}

/** Fire an ACTION_VIEW intent for the given PDF file via FileProvider. */
private fun openPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
