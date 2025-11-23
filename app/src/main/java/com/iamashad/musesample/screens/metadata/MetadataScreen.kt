package com.iamashad.musesample.screens.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.pow

/**
 * Simple, form-style screen for entering / editing session metadata.
 *
 * Required fields:
 * - Patient name
 * - Patient ID (must contain at least one digit)
 * - Age
 * - Device
 * - Height
 * - Weight
 * - Sex (always selected)
 * - Unit system (always selected)
 * - Posture (always selected)
 * - Auscultation position (always selected)
 *
 * Optional:
 * - Clinician notes
 *
 * Behaviour:
 * - If a Session already exists for this wavPath, the form is pre-filled and
 *   saving will update that session (ID, pdfPath and original sessionStart preserved).
 * - If not, a new Session is created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataScreen(
    wavPath: String,
    rawWavPath: String,
    onSaved: () -> Unit,
    vm: MetadataViewModel
) {
    val focus = LocalFocusManager.current
    val isSaving by vm.isSaving.collectAsState()

    // Look for an existing session for this wavPath (for "Edit details" flow).
    val allSessions by SessionRepository.sessions.collectAsState(initial = emptyList())
    val existingSession = remember(allSessions, wavPath) {
        allSessions.firstOrNull { it.wavPath == wavPath }
    }

    // ---- Form state (defaults cleared for all text fields) ----
    var patientName by remember { mutableStateOf(TextFieldValue("")) }
    var patientId by remember { mutableStateOf(TextFieldValue("")) }
    var device by remember { mutableStateOf(TextFieldValue("")) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var age by remember { mutableStateOf(TextFieldValue("")) }
    var sex by remember { mutableStateOf(PatientS3x.Male) }
    var unit by remember { mutableStateOf(UnitSystem.Metric) }
    var height by remember { mutableStateOf(TextFieldValue("")) }
    var weight by remember { mutableStateOf(TextFieldValue("")) }
    var posture by remember { mutableStateOf(Posture.Standing) }
    var position by remember { mutableStateOf(AuscPosition.Mitral) }

    // One-time initialization from existing session (if present).
    var initializedFromExisting by remember(wavPath) { mutableStateOf(false) }

    LaunchedEffect(existingSession?.id) {
        if (!initializedFromExisting && existingSession != null) {
            initializedFromExisting = true

            val s = existingSession

            patientName = TextFieldValue(s?.patientName.orEmpty())

            // Hide placeholder auto IDs (REC-...) from the form and treat as blank
            val rawId = s?.patientId.orEmpty()
            patientId = TextFieldValue(
                if (rawId.startsWith("REC-")) "" else rawId
            )

            age = TextFieldValue(s?.age.orEmpty())
            device = TextFieldValue(s?.deviceModel.orEmpty())
            notes = TextFieldValue(s?.notes.orEmpty())

            // Infer unit system + strip units from height/weight if present.
            val heightRaw = s?.height.orEmpty()
            val weightRaw = s?.weight.orEmpty()

            val isMetric = heightRaw.contains("cm") || weightRaw.contains("kg")
            val isImperial = heightRaw.contains("in") || weightRaw.contains("lb")

            unit = when {
                isMetric -> UnitSystem.Metric
                isImperial -> UnitSystem.Imperial
                else -> UnitSystem.Metric
            }

            val heightValue = heightRaw.split(" ").firstOrNull().orEmpty()
            val weightValue = weightRaw.split(" ").firstOrNull().orEmpty()

            if (heightValue.isNotBlank()) {
                height = TextFieldValue(heightValue)
            }
            if (weightValue.isNotBlank()) {
                weight = TextFieldValue(weightValue)
            }

            // Sex / posture / position from their labels
            sex = PatientS3x.entries.firstOrNull { it.label == s?.sex } ?: PatientS3x.Male
            posture = Posture.entries.firstOrNull { it.label == s?.posture } ?: Posture.Standing
            position =
                AuscPosition.entries.firstOrNull { it.label == s?.position } ?: AuscPosition.Mitral
        }
    }

    // ---- Derived values & validation ----
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

    val nameText = patientName.text.trim()
    val idText = patientId.text.trim()
    val ageText = age.text.trim()
    val heightText = height.text.trim()
    val weightText = weight.text.trim()
    val deviceText = device.text.trim()

    val nameOk = nameText.length >= 2

    // Mandatory & must contain at least one digit
    val idOk = idText.isNotEmpty() && idText.any { it.isDigit() }

    val ageOk = ageText.isNotEmpty() &&
            ageText.toIntOrNull()?.let { it in 0..120 } == true

    val heightOk =
        heightText.isNotEmpty() &&
                heightText.toDoubleOrNull()?.let { it in 20.0..300.0 } == true

    val weightOk =
        weightText.isNotEmpty() &&
                weightText.toDoubleOrNull()?.let { it in 1.0..500.0 } == true

    // Device is mandatory (non-blank)
    val deviceOk = deviceText.isNotEmpty()

    val formOk = nameOk && idOk && ageOk && heightOk && weightOk && deviceOk

    fun saveIfValid() {
        if (!formOk || isSaving) return
        focus.clearFocus()
        val session = buildSession(
            patientName = nameText,
            patientId = idText,
            age = ageText,
            device = deviceText,
            notes = notes.text.trim(),
            sex = sex,
            unit = unit,
            height = heightText,
            weight = weightText,
            bmi = bmi,
            posture = posture,
            position = position,
            wavPath = wavPath,
            rawWavPath = rawWavPath,
            existing = existingSession
        )

        vm.saveSessionAndGeneratePdf(
            session = session
        ) {
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session details") },
                actions = {
                    TextButton(
                        enabled = formOk && !isSaving,
                        onClick = { saveIfValid() }
                    ) {
                        Text("SAVE")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Section(title = "Patient") {
                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Name*") },
                    isError = !nameOk,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = patientId,
                    onValueChange = { patientId = it },
                    label = { Text("Patient ID*") },
                    isError = !idOk,
                    supportingText = {
                        if (!idOk && idText.isNotEmpty()) {
                            Text(
                                "ID must contain at least one number.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        state = age,
                        onChange = { age = it },
                        label = "Age*",
                        isError = !ageOk,
                        modifier = Modifier.weight(1f)
                    )
                    SimpleDropdown(
                        label = "Sex*",
                        options = PatientS3x.entries,
                        selected = sex,
                        onSelected = { sex = it },
                        optionLabel = { it.label },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider()

            Section(title = "Measurements") {
                Text(
                    text = "Unit system*",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val options = listOf(UnitSystem.Metric, UnitSystem.Imperial)
                    options.forEachIndexed { index, u ->
                        SegmentedButton(
                            selected = unit == u,
                            onClick = { unit = u },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            label = {
                                Text(
                                    if (u == UnitSystem.Metric) "Metric (cm/kg)"
                                    else "Imperial (in/lb)"
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        state = height,
                        onChange = { height = it },
                        label = "Height*",
                        suffix = if (unit == UnitSystem.Metric) "cm" else "in",
                        isError = !heightOk,
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        state = weight,
                        onChange = { weight = it },
                        label = "Weight*",
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
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            Section(title = "Session") {
                OutlinedTextField(
                    value = device,
                    onValueChange = { device = it },
                    label = { Text("Device*") },
                    isError = !deviceOk,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SimpleDropdown(
                    label = "Posture*",
                    options = Posture.entries,
                    selected = posture,
                    onSelected = { posture = it },
                    optionLabel = { it.label }
                )

                SimpleDropdown(
                    label = "Auscultation position*",
                    options = AuscPosition.entries,
                    selected = position,
                    onSelected = { position = it },
                    optionLabel = { it.label }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Clinician notes (optional)") },
                    placeholder = { Text("Optional observations") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (formOk) "All required fields are complete."
                else "Please complete the highlighted fields before saving.",
                style = MaterialTheme.typography.bodySmall,
                color = if (formOk)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(16.dp))

            // Secondary Save button at bottom for easier reach (same behaviour as AppBar action)
            Button(
                onClick = { saveIfValid() },
                enabled = formOk && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(if (isSaving) "Saving…" else "Save session")
            }
        }
    }
    if (isSaving) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* block dismiss while saving */ },
            confirmButton = {},
            title = { Text("Generating report…") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This may take a few seconds.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}

/* ---------- Helpers ---------- */

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        content()
    }
}

/**
 * Simple single-select dropdown using an exposed menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SimpleDropdown(
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
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(optionLabel(opt)) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Numeric input allowing digits and a single decimal point.
 */
@Composable
private fun NumberField(
    state: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = state,
        onValueChange = { tv ->
            val filtered = tv.text.filterIndexed { index, c ->
                c.isDigit() || (c == '.' && tv.text.indexOf('.') == index)
            }
            onChange(tv.copy(text = filtered))
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        trailingIcon = {
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Builds a Session, preserving ID / pdfPath / original timestamp when editing.
 */
private fun buildSession(
    patientName: String,
    patientId: String,
    age: String,
    device: String,
    notes: String,
    sex: PatientS3x,
    unit: UnitSystem,
    height: String,
    weight: String,
    bmi: String,
    posture: Posture,
    position: AuscPosition,
    wavPath: String,
    rawWavPath: String,
    existing: Session? = null
): Session {
    val existingTimestamp = existing?.sessionStart
    val timestamp = existingTimestamp ?: LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))

    val heightFormatted =
        if (height.isBlank()) ""
        else height + if (unit == UnitSystem.Metric) " cm" else " in"

    val weightFormatted =
        if (weight.isBlank()) ""
        else weight + if (unit == UnitSystem.Metric) " kg" else " lb"

    return existing?.copy(
        patientName = patientName,
        patientId = patientId,
        age = age,
        sessionStart = timestamp,   // keep original if present
        deviceModel = device,
        notes = notes,
        posture = posture.label,
        position = position.label,
        wavPath = wavPath,
        rawWavPath = rawWavPath,
        sex = sex.label,
        height = heightFormatted,
        weight = weightFormatted,
        bmi = bmi
        // pdfPath and id are preserved automatically
    )
        ?: Session(
            patientName = patientName,
            patientId = patientId,
            age = age,
            sessionStart = timestamp,
            deviceModel = device,
            notes = notes,
            posture = posture.label,
            position = position.label,
            wavPath = wavPath,
            rawWavPath = rawWavPath,
            sex = sex.label,
            height = heightFormatted,
            weight = weightFormatted,
            bmi = bmi
        )
}
