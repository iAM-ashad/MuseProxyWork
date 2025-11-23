package com.iamashad.musesample.screens.session

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.iamashad.musesample.R
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Session history v2.0
 *
 * Changes from v1:
 * - Cards are simplified: focus on date, metadata status, and PDF status.
 * - We no longer depend on posture/position/BMI/device for the primary UI.
 * - "Generate PDF" now means "Add/Edit metadata & generate PDF" and is delegated
 *   via [onEditMetadataAndGeneratePdf].
 *
 * Functionality preserved:
 * - Observe sessions Flow from repository.
 * - Search by patient / ID (when metadata exists).
 * - Filter by PDF status + time range.
 * - Sort by Date / Has PDF.
 * - Multi-select share (PDFs) and delete.
 * - Per-item actions: play WAV, open/share PDF, delete.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    onBack: () -> Unit,
    /** Called when user taps "Generate / Edit PDF" on a session card. */
    onEditMetadataAndGeneratePdf: (Session) -> Unit
) {
    val ctx = LocalContext.current

    // Source of truth: encrypted DB via repository
    val sessionsOrNull: List<Session>? by SessionRepository.sessions.collectAsState(initial = null)
    val isLoading = sessionsOrNull == null
    val sessions = sessionsOrNull.orEmpty()

    // ---- Filter + sort state ----
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var pdfFilter by remember { mutableStateOf(PdfFilter.All) }
    var timeFilter by remember { mutableStateOf(TimeFilter.All) }
    var sortKey by remember { mutableStateOf(SortKey.Date) }
    var sortOrder by remember { mutableStateOf(SortOrder.Desc) }

    // Bottom sheet (filters)
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    // Multi-select
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // ---- Filtering + sorting pipeline ----
    val today = LocalDate.now()
    val filtered = remember(
        sessions,
        query.text,
        pdfFilter,
        timeFilter,
        sortKey,
        sortOrder
    ) {
        sessions
            .asSequence()
            // Text search: matches patient name / ID when present
            .filter { s ->
                val q = query.text.trim()
                if (q.isBlank()) true else {
                    s.patientName.contains(q, ignoreCase = true) ||
                            s.patientId.contains(q, ignoreCase = true)
                }
            }
            // PDF status filter
            .filter { s ->
                when (pdfFilter) {
                    PdfFilter.All -> true
                    PdfFilter.With -> !s.pdfPath.isNullOrBlank()
                    PdfFilter.Without -> s.pdfPath.isNullOrBlank()
                }
            }
            // Time window filter (using display date string)
            .filter { s ->
                when (timeFilter) {
                    TimeFilter.All -> true
                    TimeFilter.Last7 -> isWithinDays(s.sessionStart, 7, today)
                    TimeFilter.Last30 -> isWithinDays(s.sessionStart, 30, today)
                }
            }
            .toList()
            .let { list ->
                val sorted = when (sortKey) {
                    SortKey.Date -> list.sortedWith(compareBy { it.sessionStart })
                    SortKey.Pdf -> list.sortedBy { it.pdfPath.isNullOrBlank() } // with-PDF first
                }
                if (sortOrder == SortOrder.Desc) sorted.reversed() else sorted
            }
    }

    // Keep selection valid as visible list changes
    LaunchedEffect(filtered) {
        val currentIds = filtered.map { it.id }.toSet()
        selectedIds = selectedIds intersect currentIds
    }

    // ---- Bulk actions ----

    /** Share all selected PDFs in a single SEND_MULTIPLE intent. */
    fun shareSelected() {
        val files = filtered
            .filter { it.id in selectedIds }
            .mapNotNull { it.pdfPath }
            .map(::File)
            .filter { it.exists() }
        if (files.isEmpty()) return

        val uris = files.map {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", it)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share PDFs"))
    }

    /** Delete all selected sessions from the encrypted database. */
    fun deleteSelected() {
        val targets = filtered.filter { it.id in selectedIds }
        targets.forEach { SessionRepository.delete(it) }
        selectedIds = emptySet()
    }

    val isFiltered =
        query.text.isNotBlank() ||
                pdfFilter != PdfFilter.All ||
                timeFilter != TimeFilter.All ||
                sortKey != SortKey.Date ||
                sortOrder != SortOrder.Desc

    // ---- Scaffold: top bar switches on selection mode ----
    Scaffold(
        topBar = {
            if (selectionMode) {
                CenterAlignedTopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit selection"
                            )
                        }
                    },
                    actions = {
                        val allIds = filtered.map { it.id }.toSet()
                        val allSelected = selectedIds.size == allIds.size && allIds.isNotEmpty()

                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else allIds
                        }) {
                            Icon(
                                painter = if (allSelected)
                                    painterResource(R.drawable.clear_selection)
                                else
                                    painterResource(R.drawable.checkbox),
                                contentDescription = if (allSelected) "Clear selection" else "Select all"
                            )
                        }

                        IconButton(
                            onClick = { shareSelected() },
                            enabled = filtered.any { it.id in selectedIds && !it.pdfPath.isNullOrBlank() }
                        ) {
                            Icon(
                                painterResource(R.drawable.share),
                                contentDescription = "Share PDFs"
                            )
                        }

                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Session history") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBottomSheet = true }) {
                            Icon(
                                painterResource(R.drawable.filters),
                                contentDescription = "Open filters"
                            )
                        }
                        if (isFiltered) {
                            TextButton(onClick = {
                                query = TextFieldValue("")
                                pdfFilter = PdfFilter.All
                                timeFilter = TimeFilter.All
                                sortKey = SortKey.Date
                                sortOrder = SortOrder.Desc
                            }) { Text("Clear") }
                        }
                    }
                )
            }
        }
    ) { pad ->
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 16.dp)
            ) {
                // Search bar is hidden while multi-select is active
                if (!selectionMode) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Search by patient or ID (when available)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (filtered.isEmpty()) {
                    EmptyState()
                } else {
                    // Group by day for readability
                    val grouped = filtered
                        .groupBy { parseToLocalDate(it.sessionStart) ?: LocalDate.MIN }
                        .toSortedMap(compareByDescending { it })

                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        grouped.forEach { (day, list) ->
                            stickyHeader {
                                Text(
                                    text = if (day == LocalDate.MIN) "Undated"
                                    else day.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                            items(list, key = { it.id }) { s ->
                                val isSelectedItem = s.id in selectedIds
                                SessionCard(
                                    session = s,
                                    selectionEnabled = selectionMode,
                                    selected = isSelectedItem,
                                    onToggleSelect = {
                                        selectedIds =
                                            if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                    },
                                    onPlay = {
                                        if (selectionMode) {
                                            selectedIds =
                                                if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                            return@SessionCard
                                        }
                                        val wav = File(s.wavPath)
                                        if (!wav.exists()) return@SessionCard
                                        val uri = FileProvider.getUriForFile(
                                            ctx, "${ctx.packageName}.fileprovider", wav
                                        )
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "audio/wav")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        })
                                    },
                                    onEditMetadataAndGeneratePdf = {
                                        if (selectionMode) {
                                            selectedIds =
                                                if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                            return@SessionCard
                                        }
                                        onEditMetadataAndGeneratePdf(s)
                                    },
                                    onOpenPdf = {
                                        if (selectionMode) {
                                            selectedIds =
                                                if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                            return@SessionCard
                                        }
                                        s.pdfPath?.let { path ->
                                            val file = File(path)
                                            if (file.exists()) {
                                                val uri = FileProvider.getUriForFile(
                                                    ctx, "${ctx.packageName}.fileprovider", file
                                                )
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                })
                                            }
                                        }
                                    },
                                    onSharePdf = {
                                        if (selectionMode) {
                                            selectedIds =
                                                if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                            return@SessionCard
                                        }
                                        s.pdfPath?.let { path ->
                                            val file = File(path)
                                            if (file.exists()) {
                                                val uri = FileProvider.getUriForFile(
                                                    ctx, "${ctx.packageName}.fileprovider", file
                                                )
                                                ctx.startActivity(Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                })
                                            }
                                        }
                                    },
                                    onDelete = {
                                        if (selectionMode) {
                                            selectedIds =
                                                if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                        } else {
                                            SessionRepository.delete(s)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Filters Bottom Sheet ----
    if (showBottomSheet) {
        var draftPdf by remember(pdfFilter) { mutableStateOf(pdfFilter) }
        var draftTime by remember(timeFilter) { mutableStateOf(timeFilter) }
        var draftSortKey by remember(sortKey) { mutableStateOf(sortKey) }
        var draftSortOrder by remember(sortOrder) { mutableStateOf(sortOrder) }

        ModalBottomSheet(
            onDismissRequest = { },
            sheetState = bottomSheetState
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                // PDF state
                Text("PDF status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow {
                    PdfFilter.entries.forEachIndexed { index, opt ->
                        SegmentedButton(
                            selected = draftPdf == opt,
                            onClick = { draftPdf = opt },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                PdfFilter.entries.size
                            ),
                            label = { Text(opt.label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Time range
                Text("Time range", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow {
                    TimeFilter.entries.forEachIndexed { index, tf ->
                        val lbl = when (tf) {
                            TimeFilter.All -> "All"
                            TimeFilter.Last7 -> "Last 7 days"
                            TimeFilter.Last30 -> "Last 30 days"
                        }
                        SegmentedButton(
                            selected = draftTime == tf,
                            onClick = { draftTime = tf },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                TimeFilter.entries.size
                            ),
                            label = { Text(lbl) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Sort
                Text("Sort by", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow {
                    SortKey.entries.forEachIndexed { index, key ->
                        SegmentedButton(
                            selected = draftSortKey == key,
                            onClick = { draftSortKey = key },
                            shape = SegmentedButtonDefaults.itemShape(index, SortKey.entries.size),
                            label = { Text(key.label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Order", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        selected = draftSortOrder == SortOrder.Desc,
                        onClick = { draftSortOrder = SortOrder.Desc },
                        label = { Text("Desc") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.arrow_downward),
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = draftSortOrder == SortOrder.Asc,
                        onClick = { draftSortOrder = SortOrder.Asc },
                        label = { Text("Asc") },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.arrow_upward),
                                contentDescription = null
                            )
                        }
                    )
                }

                // Actions
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        draftPdf = PdfFilter.All
                        draftTime = TimeFilter.All
                        draftSortKey = SortKey.Date
                        draftSortOrder = SortOrder.Desc
                    }) { Text("RESET") }

                    Button(onClick = {
                        pdfFilter = draftPdf
                        timeFilter = draftTime
                        sortKey = draftSortKey
                        sortOrder = draftSortOrder
                    }) { Text("APPLY") }
                }
            }
        }
    }

    // ---- Bulk Delete Confirmation ----
    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Delete ${selectedIds.size} session(s)") },
            text = {
                Text("Are you sure you want to delete the selected sessions? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSelected()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/* ---------- Card + helpers ---------- */

/**
 * One row in the list representing a saved session.
 *
 * v2.0 UI:
 * - Header: patient name / ID when present, otherwise a generic label.
 * - Subheader: timestamp.
 * - Status chips: metadata status, PDF status.
 * - Actions: Play, Generate/Edit PDF, Open/Share PDF, Delete.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: Session,
    selectionEnabled: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onEditMetadataAndGeneratePdf: () -> Unit,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val hasPdf = !session.pdfPath.isNullOrBlank()

    // Detect placeholder sessions inserted automatically after recording
    val isPlaceholder =
        session.patientName.isBlank() &&
                session.patientId.startsWith("REC-") &&
                session.deviceModel.isBlank() &&
                session.notes.isBlank()

    // Metadata considered present only if it's not a placeholder
    val hasMetadata = !isPlaceholder && (
            session.patientName.isNotBlank() ||
                    session.patientId.isNotBlank()
            )

    // Display title prefers patient name / ID, falls back to generic.
    val title = when {
        isPlaceholder -> "New Recording"
        session.patientName.isNotBlank() && session.patientId.isNotBlank() ->
            "${session.patientName} • ${session.patientId}"

        session.patientName.isNotBlank() -> session.patientName
        session.patientId.isNotBlank() -> "ID: ${session.patientId}"
        else -> "New Recording"
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionEnabled) onToggleSelect() else onPlay() },
                onLongClick = { onToggleSelect() }
            ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row with title + optional checkbox / PDF icons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
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

                if (selectionEnabled) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                } else {
                    if (hasPdf) {
                        Row {
                            IconButton(onClick = onOpenPdf) {
                                Icon(
                                    painterResource(R.drawable.picture_as_pdf),
                                    contentDescription = "Open PDF"
                                )
                            }
                            IconButton(onClick = onSharePdf) {
                                Icon(
                                    painterResource(R.drawable.share),
                                    contentDescription = "Share PDF"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Status row: metadata + PDF state
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = hasMetadata,
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(if (hasMetadata) "Details added" else "Details not added")
                    },
                    leadingIcon = if (hasMetadata) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null
                )

                FilterChip(
                    selected = hasPdf,
                    onClick = {},
                    enabled = false,
                    label = { Text(if (hasPdf) "PDF available" else "No PDF") }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Primary action row (hidden in selection mode)
            if (!selectionEnabled) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }

                    Button(onClick = onEditMetadataAndGeneratePdf) {
                        Icon(
                            painterResource(R.drawable.picture_as_pdf),
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (hasPdf || hasMetadata)
                                "Edit details & regenerate PDF"
                            else
                                "Add details & generate PDF"
                        )
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Delete session") },
            text = {
                Text("Are you sure you want to delete this session? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            }
        )
    }
}


/** Empty-results placeholder copy. */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No sessions to show", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Record a new session or adjust your filters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------- Date helpers ---------- */

/** Parse the display timestamp (e.g., "05 Oct 2025, 14:30") into a LocalDate. */
private fun parseToLocalDate(display: String): LocalDate? = runCatching {
    val dt =
        java.time.LocalDateTime.parse(display, DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
    dt.toLocalDate()
}.getOrNull()

/** True if the display date is within the last [days] days from [today] (inclusive). */
private fun isWithinDays(display: String, days: Int, today: LocalDate): Boolean {
    val d = parseToLocalDate(display) ?: return false
    return !d.isBefore(today.minusDays(days.toLong()))
}
