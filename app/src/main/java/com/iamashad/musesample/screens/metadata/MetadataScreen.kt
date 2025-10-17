package com.iamashad.musesample.screens.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.model.Session
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    wavPath: String,
    onSaved: () -> Unit,
    vm: MetadataViewModel
) {
    val focus = LocalFocusManager.current

    // --- STATE ---
    var patientName by remember { mutableStateOf(TextFieldValue("Jane Doe")) }
    var patientId by remember { mutableStateOf(TextFieldValue("P-123456")) }
    var device by remember { mutableStateOf(TextFieldValue("TAAL Stethoscope")) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var age by remember { mutableStateOf(TextFieldValue("37")) }
    var s3x by remember { mutableStateOf(PatientS3x.Male) }
    var unit by remember { mutableStateOf(UnitSystem.Metric) }
    var height by remember { mutableStateOf(TextFieldValue("165")) }
    var weight by remember { mutableStateOf(TextFieldValue("68")) }
    var posture by remember { mutableStateOf(Posture.Standing) }
    var position by remember { mutableStateOf(AuscPosition.Mitral) }

    // --- DERIVED STATE & VALIDATION ---
    val bmi by remember {
        derivedStateOf {
            val h = height.text.toDoubleOrNull()
            val w = weight.text.toDoubleOrNull()
            if (h == null || w == null || h == 0.0) "" else {
                val bmiVal = when (unit) {
                    UnitSystem.Metric -> w / (h / 100.0).pow(2.0)
                    UnitSystem.Imperial -> (703.0 * w) / h.pow(2.0)
                }
                "%.1f".format(bmiVal)
            }
        }
    }
    val nameOk = patientName.text.trim().length >= 2
    val idOk = patientId.text.trim().isNotEmpty()
    val ageOk = age.text.trim().toIntOrNull()?.let { it in 0..120 } == true
    val heightOk =
        height.text.isBlank() || height.text.toDoubleOrNull()?.let { it in 20.0..300.0 } == true
    val weightOk =
        weight.text.isBlank() || weight.text.toDoubleOrNull()?.let { it in 1.0..500.0 } == true
    val formOk = nameOk && idOk && ageOk && heightOk && weightOk

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Enter Session Metadata") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (formOk) { // Guard clause to prevent clicks when disabled
                        focus.clearFocus()

                        val session = Session(
                            patientName = patientName.text.trim(),
                            patientId = patientId.text.trim(),
                            age = age.text.trim(),
                            sessionStart = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")),
                            deviceModel = device.text.trim(),
                            notes = notes.text.trim(),
                            posture = posture.label,
                            position = position.label,
                            wavPath = wavPath,
                            sex = s3x.label,
                            height = if (height.text.isBlank()) "" else height.text + if (unit == UnitSystem.Metric) " cm" else " in",
                            weight = if (weight.text.isBlank()) "" else weight.text + if (unit == UnitSystem.Metric) " kg" else " lb",
                            bmi = bmi
                        )
                        val meta = PcgReportMeta(
                            patientName = session.patientName,
                            patientId = session.patientId,
                            sessionStart = session.sessionStart,
                            deviceModel = session.deviceModel,
                            notes = session.notes,
                            age = session.age,
                            sex = "Sex: ${s3x.label}",
                            height = session.height,
                            weight = session.weight,
                            bmi = session.bmi,
                            posture = session.posture,
                            position = session.position
                        )
                        vm.saveSession(session)
                        onSaved()
                    }
                },
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Save, contentDescription = "Save")
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Save Session")
                    }
                },
                // Manually set colors to reflect enabled/disabled state
                containerColor = if (formOk) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.12f
                ),
                contentColor = if (formOk) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.38f
                )
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FormSection(title = "Patient Details") {
                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text("Name*") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        isError = !nameOk,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = patientId,
                        onValueChange = { patientId = it },
                        label = { Text("Patient ID*") },
                        leadingIcon = { Icon(Icons.Filled.Badge, null) },
                        isError = !idOk,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            state = age,
                            onChange = { age = it },
                            label = "Age*",
                            isError = !ageOk,
                            leadingIcon = { Icon(Icons.Filled.Cake, null) },
                            modifier = Modifier.weight(1f)
                        )
                        LabeledDropdown(
                            label = "Sex",
                            options = PatientS3x.entries,
                            selected = s3x,
                            onSelected = { s3x = it },
                            optionLabel = { it.label },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                FormSection(title = "Measurements") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(UnitSystem.Metric, UnitSystem.Imperial).forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = unit == u,
                                onClick = { unit = u },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                                label = { Text(if (u == UnitSystem.Metric) "Metric (cm/kg)" else "Imperial (in/lb)") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            state = height,
                            onChange = { height = it },
                            label = "Height",
                            suffix = if (unit == UnitSystem.Metric) "cm" else "in",
                            isError = !heightOk,
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            state = weight,
                            onChange = { weight = it },
                            label = "Weight",
                            suffix = if (unit == UnitSystem.Metric) "kg" else "lb",
                            isError = !weightOk,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = TextFieldValue(bmi),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("BMI (auto-calculated)") },
                        leadingIcon = { Icon(Icons.Filled.Scale, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                FormSection(title = "Session & Notes") {
                    OutlinedTextField(
                        value = device,
                        onValueChange = { device = it },
                        label = { Text("Device") },
                        leadingIcon = { Icon(Icons.Filled.MonitorHeart, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LabeledDropdown(
                        label = "Posture",
                        options = Posture.entries,
                        selected = posture,
                        onSelected = { posture = it },
                        optionLabel = { it.label })
                    LabeledDropdown(
                        label = "Auscultation Position",
                        options = AuscPosition.entries,
                        selected = position,
                        onSelected = { position = it },
                        optionLabel = { it.label })
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Clinician Notes") },
                        placeholder = { Text("Optional observations...") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Helper composable for creating a visually distinct section in the form. */
@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/** Helper composable for a reusable dropdown menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(
                    text = { Text(optionLabel(it)) },
                    onClick = { onSelected(it); expanded = false })
            }
        }
    }
}

/** Helper composable for a number input field that filters for digits and one decimal point. */
@Composable
private fun NumberField(
    state: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = state,
        onValueChange = { tv ->
            val filtered =
                tv.text.filterIndexed { index, c -> c.isDigit() || (c == '.' && tv.text.indexOf('.') == index) }
            onChange(tv.copy(text = filtered))
        },
        label = { Text(label) },
        leadingIcon = leadingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        trailingIcon = {
            if (suffix != null) Text(
                suffix,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        modifier = modifier.fillMaxWidth()
    )
}