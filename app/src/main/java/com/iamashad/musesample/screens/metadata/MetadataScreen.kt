package com.iamashad.musesample.screens.metadata

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.iamashad.musesample.model.Session
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MetadataScreen(
    wavPath: String,
    onSaved: () -> Unit,
    vm: MetadataViewModel
) {
    var patientName by remember { mutableStateOf(TextFieldValue("Jane Doe")) }
    var patientId by remember { mutableStateOf(TextFieldValue("P-123456")) }
    var device by remember { mutableStateOf(TextFieldValue("TAAL Stethoscope")) }
    var notes by remember { mutableStateOf(TextFieldValue("Test capture")) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Session Metadata", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(patientName, { patientName = it }, label = { Text("Patient Name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(patientId, { patientId = it }, label = { Text("Patient ID") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(device, { device = it }, label = { Text("Device") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val session = Session(
                    patientName = patientName.text,
                    patientId = patientId.text,
                    sessionStart = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")),
                    deviceModel = device.text,
                    notes = notes.text,
                    wavPath = wavPath
                )
                vm.saveSession(session)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Session") }
    }
}