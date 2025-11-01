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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.iamashad.musesample.generatePcgPdf
import com.iamashad.musesample.model.PcgReportMeta
import com.iamashad.musesample.model.Session
import com.iamashad.musesample.repository.SessionRepository
import com.iamashad.musesample.widgets.ElegantAlertDialog
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Session history screen.
 *
 * Responsibilities:
 * - Observe sessions Flow from repository and show loading until first emission.
 * - Provide client-side filtering (search, position, time range, PDF state).
 * - Provide client-side sorting and multi-select actions (share/delete).
 * - Per-item actions: play WAV, generate/regenerate PDF, open/share PDF, delete.
 *
 * Notes:
 * - Filtering uses the display date string; time windows (7/30 days) compare by LocalDate.
 * - PDF generation uses the current session metadata to render a 2-page report via WebView.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Source of truth
    val sessionsOrNull: List<Session>? by SessionRepository.sessions.collectAsState(initial = null)
    val isLoading = sessionsOrNull == null
    val sessions = sessionsOrNull.orEmpty()

    // ---- Filter state (committed) ----
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var pdfFilter by remember { mutableStateOf(PdfFilter.All) }
    var positionFilter by remember { mutableStateOf("All") }
    var timeFilter by remember { mutableStateOf(TimeFilter.All) }
    var sortKey by remember { mutableStateOf(SortKey.Date) }
    var sortOrder by remember { mutableStateOf(SortOrder.Desc) }

    // Position facets for chips + searchable list in bottom sheet
    val uniquePositions =
        remember(sessions) { listOf("All") + sessions.map { it.position }.distinct().sorted() }
    val topPositions = remember(uniquePositions) { uniquePositions.filter { it != "All" }.take(8) }

    // Bottom sheet (filters)
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    // Multi-select
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // ---- Filtering + sorting pipeline ----
    val today = LocalDate.now()
    val filtered =
        remember(sessions, query.text, pdfFilter, positionFilter, timeFilter, sortKey, sortOrder) {
            sessions
                .asSequence()
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
                        SortKey.Name -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.patientName })
                        SortKey.Position -> list.sortedBy { it.position }
                        SortKey.Pdf -> list.sortedBy { it.pdfPath.isNullOrBlank() } // with-PDF first
                    }
                    if (sortOrder == SortOrder.Desc) sorted.reversed() else sorted
                }
        }

    // Keep selection valid as the visible list changes
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
                positionFilter != "All" ||
                timeFilter != TimeFilter.All ||
                sortKey != SortKey.Date || sortOrder != SortOrder.Desc

    // ---- Scaffolding: top bars vary by selection mode ----
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

                        // Toggle select all / clear
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else allIds
                        }) {
                            Icon(
                                painter = if (allSelected) painterResource(R.drawable.clear_selection) else painterResource(
                                    R.drawable.checkbox
                                ),
                                contentDescription = if (allSelected) "Clear selection" else "Select all"
                            )
                        }

                        // Share PDFs (enabled only if any selected has a PDF)
                        IconButton(
                            onClick = { shareSelected() },
                            enabled = filtered.any { it.id in selectedIds && !it.pdfPath.isNullOrBlank() }
                        ) {
                            Icon(
                                painterResource(R.drawable.share),
                                contentDescription = "Share PDFs"
                            )
                        }

                        // Delete
                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Session History") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                                positionFilter = "All"
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
            // First emission not yet available
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
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
                        label = { Text("Search by patient or ID") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (filtered.isEmpty()) {
                    EmptyState()
                } else {
                    // Group by day for compact readability
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
                                        // Tap acts as select when in selection mode
                                        if (selectionMode) {
                                            selectedIds =
                                                if (isSelectedItem) selectedIds - s.id else selectedIds + s.id
                                            return@SessionCard
                                        }
                                        // Launch external audio player
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
                                    onGeneratePdf = {
                                        if (selectionMode) return@SessionCard
                                        // Render PDF off the main thread and persist path
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
        // Draft copies so user can cancel changes
        var draftPdf by remember(pdfFilter) { mutableStateOf(pdfFilter) }
        var draftPosition by remember(positionFilter) { mutableStateOf(positionFilter) }
        var draftTime by remember(timeFilter) { mutableStateOf(timeFilter) }
        var draftSortKey by remember(sortKey) { mutableStateOf(sortKey) }
        var draftSortOrder by remember(sortOrder) { mutableStateOf(sortOrder) }
        var positionSearch by remember { mutableStateOf(TextFieldValue("")) }

        val allPositionsNoAll = remember(uniquePositions) { uniquePositions.filter { it != "All" } }
        val filteredPositions = remember(allPositionsNoAll, positionSearch.text) {
            val q = positionSearch.text.trim()
            if (q.isBlank()) allPositionsNoAll
            else allPositionsNoAll.filter { it.contains(q, ignoreCase = true) }
        }

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
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
                            TimeFilter.Last7 -> "Last 7d"
                            TimeFilter.Last30 -> "Last 30d"
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

                // Position single-select chips (popular first, then full list)
                Text("Position", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All" chip
                    FilterChip(
                        selected = draftPosition == "All",
                        onClick = { draftPosition = "All" },
                        label = { Text("All") },
                        leadingIcon = if (draftPosition == "All") {
                            { Icon(Icons.Filled.Check, null) }
                        } else null
                    )
                    // Popular
                    topPositions.forEach { pos ->
                        FilterChip(
                            selected = draftPosition == pos,
                            onClick = { draftPosition = pos },
                            label = { Text(pos) },
                            leadingIcon = if (draftPosition == pos) {
                                { Icon(Icons.Filled.Check, null) }
                            } else null
                        )
                    }
                    // Full list (minus already shown)
                    filteredPositions.filterNot { it in topPositions }.forEach { pos ->
                        FilterChip(
                            selected = draftPosition == pos,
                            onClick = { draftPosition = pos },
                            label = { Text(pos) },
                            leadingIcon = if (draftPosition == pos) {
                                { Icon(Icons.Filled.Check, null) }
                            } else null
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
                        leadingIcon = { Icon(painterResource(R.drawable.arrow_downward), null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = draftSortOrder == SortOrder.Asc,
                        onClick = { draftSortOrder = SortOrder.Asc },
                        label = { Text("Asc") },
                        leadingIcon = { Icon(painterResource(R.drawable.arrow_upward), null) }
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
                        draftPosition = "All"
                        draftTime = TimeFilter.All
                        draftSortKey = SortKey.Date
                        draftSortOrder = SortOrder.Desc
                        positionSearch = TextFieldValue("")
                    }) { Text("RESET") }

                    Button(onClick = {
                        pdfFilter = draftPdf
                        positionFilter = draftPosition
                        timeFilter = draftTime
                        sortKey = draftSortKey
                        sortOrder = draftSortOrder
                        showBottomSheet = false
                    }) { Text("APPLY") }
                }
            }
        }
    }

    // ---- Bulk Delete Confirmation ----
    if (showBulkDeleteDialog) {
        ElegantAlertDialog(
            title = "Delete ${selectedIds.size} session(s)",
            message = "Are you sure you want to delete the selected sessions? This action cannot be undone.",
            onConfirm = {
                deleteSelected()
                showBulkDeleteDialog = false
            },
            onDismiss = { showBulkDeleteDialog = false },
            confirmText = "Delete",
            dismissText = "Cancel",
            icon = Icons.Filled.Delete,
            iconTint = MaterialTheme.colorScheme.error
        )
    }
}

/* ---------- Card + helpers ---------- */

/**
 * One row in the list representing a saved session, with:
 * - Primary actions: Play WAV, Generate/Regenerate PDF.
 * - Secondary actions: Open/Share PDF (when available), Delete.
 * - Supports long-press to enter selection mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: Session,
    selectionEnabled: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onGeneratePdf: () -> Unit,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
            // Header: patient and timestamp; when selecting, show checkbox on the right
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

                if (selectionEnabled) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                } else {
                    // Quick PDF actions when not selecting
                    Row {
                        if (!session.pdfPath.isNullOrBlank()) {
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

            // Metadata chips (position, posture, BMI, device)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(onClick = {}, label = { Text(session.position) })
                AssistChip(onClick = {}, label = { Text(session.posture) })
                if (session.bmi.isNotBlank()) AssistChip(
                    onClick = {},
                    label = { Text("BMI ${session.bmi}") })
                if (session.deviceModel.isNotBlank()) AssistChip(
                    onClick = {},
                    label = { Text(session.deviceModel) })
            }

            Spacer(Modifier.height(12.dp))

            // Primary actions row
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

                    if (session.pdfPath.isNullOrBlank()) {
                        Button(onClick = onGeneratePdf) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Generate PDF")
                        }
                    } else {
                        Button(onClick = onGeneratePdf) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Regenerate PDF")
                        }
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

    // Per-item delete confirmation
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
            icon = Icons.Filled.Delete,
            iconTint = MaterialTheme.colorScheme.error
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
        Text("No matching sessions", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Try adjusting search or filters.",
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
