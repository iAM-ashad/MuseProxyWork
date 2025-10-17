package com.iamashad.musesample.screens.session

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.compose.rememberNavController
import com.iamashad.musesample.generatePcgPdf
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import com.iamashad.musesample.widgets.dialogs.ElegantAlertDialog
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// region Main Screen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by SessionRepository.sessions.collectAsState(initial = emptyList())

    // Filters
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var pdfFilter by remember { mutableStateOf(PdfFilter.All) }
    var positionFilter by remember { mutableStateOf("All") }

    val uniquePositions: List<String> = remember(sessions) {
        listOf("All") + sessions.map { it.position }.distinct().sorted()
    }

    val filtered = remember(sessions, query.text, pdfFilter, positionFilter) {
        sessions.asSequence()
            .filter { s ->
                query.text.isBlank() ||
                        s.patientName.contains(query.text, ignoreCase = true) ||
                        s.patientId.contains(query.text, ignoreCase = true)
            }
            .filter { s ->
                when (pdfFilter) {
                    PdfFilter.All -> true
                    PdfFilter.With -> !s.pdfPath.isNullOrBlank()
                    PdfFilter.Without -> s.pdfPath.isNullOrBlank()
                }
            }
            .filter { s -> positionFilter == "All" || s.position == positionFilter }
            .sortedByDescending { it.sessionStart }
            .toList()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Session History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { query = TextFieldValue("") }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear Filters")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
        ) {
            // 🔍 Search & Filters
            SessionFilterBar(
                query = query,
                onQueryChange = { query = it },
                pdfFilter = pdfFilter,
                onPdfFilterChange = { pdfFilter = it },
                positionFilter = positionFilter,
                onPositionFilterChange = { positionFilter = it },
                positionOptions = uniquePositions
            )

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = filtered.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyState()
            }

            if (filtered.isNotEmpty()) {
                val grouped =
                    filtered.groupBy { parseToLocalDate(it.sessionStart) ?: LocalDate.MIN }
                        .toSortedMap(compareByDescending { it })

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    grouped.forEach { (day, list) ->
                        stickyHeader {
                            val dayStr =
                                if (day == LocalDate.MIN) "Undated" else day.format(
                                    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")
                                )
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = dayStr,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                                )
                            }
                        }
                        items(list, key = { it.id }) { s ->
                            SessionCard(
                                session = s,
                                onPlay = {
                                    val wav = File(s.wavPath)
                                    if (!wav.exists()) return@SessionCard
                                    val uri = FileProvider.getUriForFile(
                                        ctx,
                                        "${ctx.packageName}.fileprovider",
                                        wav
                                    )
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "audio/wav")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                },
                                onGeneratePdf = {
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
                                                age = s.age,
                                                sex = s.sex,
                                                height = s.height,
                                                weight = s.weight,
                                                bmi = s.bmi,
                                                posture = s.posture,
                                                position = s.position
                                            )
                                        )
                                        SessionRepository.updatePdf(s.id, pdf.absolutePath)
                                    }
                                },
                                onOpenPdf = {
                                    s.pdfPath?.let { path ->
                                        val file = File(path)
                                        if (file.exists()) {
                                            val uri = FileProvider.getUriForFile(
                                                ctx,
                                                "${ctx.packageName}.fileprovider",
                                                file
                                            )
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            })
                                        }
                                    }
                                },
                                onSharePdf = {
                                    s.pdfPath?.let { path ->
                                        val file = File(path)
                                        if (file.exists()) {
                                            val uri = FileProvider.getUriForFile(
                                                ctx,
                                                "${ctx.packageName}.fileprovider",
                                                file
                                            )
                                            ctx.startActivity(Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            })
                                        }
                                    }
                                },
                                onDelete = { SessionRepository.delete(s) }
                            )
                        }
                    }
                }
            }
        }
    }
}
// endregion

// region Composables

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionFilterBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    pdfFilter: PdfFilter,
    onPdfFilterChange: (PdfFilter) -> Unit,
    positionFilter: String,
    onPositionFilterChange: (String) -> Unit,
    positionOptions: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search by patient or ID") },
            singleLine = true
        )

        SingleChoiceSegmentedButtonRow {
            PdfFilter.entries.forEachIndexed { index, pf ->
                SegmentedButton(
                    selected = pf == pdfFilter,
                    onClick = { onPdfFilterChange(pf) },
                    shape = SegmentedButtonDefaults.itemShape(index, PdfFilter.entries.size),
                    label = { Text(pf.label) }
                )
            }
        }

        ExposedDropdownMenuBox(
            expanded = remember { mutableStateOf(false) }.value,
            onExpandedChange = {}
        ) {
            var expanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = positionFilter,
                onValueChange = {},
                readOnly = true,
                label = { Text("Position") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                positionOptions.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onPositionFilterChange(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: Session,
    onPlay: () -> Unit,
    onGeneratePdf: () -> Unit,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${session.patientName}  •  ${session.patientId}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = session.sessionStart,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    if (!session.pdfPath.isNullOrBlank()) {
                        IconButton(onClick = onOpenPdf) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Open PDF")
                        }
                        IconButton(onClick = onSharePdf) {
                            Icon(Icons.Default.IosShare, contentDescription = "Share PDF")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(onClick = {}, label = { Text(session.position) })
                AssistChip(onClick = {}, label = { Text(session.posture) })
                if (session.bmi.isNotBlank()) AssistChip(onClick = {}, label = { Text("BMI ${session.bmi}") })
                if (session.deviceModel.isNotBlank()) AssistChip(onClick = {}, label = { Text(session.deviceModel) })
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }

                if (session.pdfPath.isNullOrBlank()) {
                    Button(onClick = onGeneratePdf) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Generate PDF")
                    }
                } else {
                    Button(onClick = onOpenPdf) {
                        Icon(Icons.Default.PictureAsPdf, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open PDF")
                    }
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteDialog) {
        ElegantAlertDialog(
            title = "Delete Session",
            message = "Are you sure you want to delete this session? This action cannot be undone.",
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            onDismiss = { showDeleteDialog = false },
            confirmText = "Delete",
            dismissText = "Cancel",
            icon = Icons.Default.Delete,
            iconTint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No matching sessions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Try adjusting search or filters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
// endregion

// region Helpers

private enum class PdfFilter(val label: String) {
    All("All"), With("With PDF"), Without("No PDF")
}

private fun parseToLocalDate(display: String): LocalDate? {
    return runCatching {
        val dt = java.time.LocalDateTime.parse(display, DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
        dt.toLocalDate()
    }.getOrNull()
}

@Preview
@Composable
fun PreviewSessionList() {
    val navController = rememberNavController()
    SessionListScreen { navController.popBackStack() }
}
// endregion
