package com.iamashad.musesample.screens.session

/**
 * Filter for whether a session already has a generated PDF.
 * Used to narrow the session list in history.
 */
enum class PdfFilter(val label: String) {
    /** Show all sessions regardless of PDF state. */
    All("All"),
    /** Show only sessions that already have a PDF path. */
    With("With PDF"),
    /** Show only sessions that do not have a PDF yet. */
    Without("No PDF")
}

/**
 * Time-window filter applied against the session's display date.
 */
enum class TimeFilter {
    /** No time restriction. */
    All,
    /** Sessions within the last 7 calendar days (inclusive). */
    Last7,
    /** Sessions within the last 30 calendar days (inclusive). */
    Last30
}

/**
 * Sort keys available for the session list.
 * Display labels are used in the filter sheet UI.
 */
enum class SortKey(val label: String) {
    /** Sort by session start display string (chronological). */
    Date("Date"),
    /** Sort by patient name (A→Z / Z→A). */
    Name("Name"),
    /** Sort by auscultation position label. */
    Position("Position"),
    /** Sort by presence of a PDF (with-PDF first when ascending). */
    Pdf("Has PDF")
}

/**
 * Sort direction applied to the chosen [SortKey].
 */
enum class SortOrder {
    /** Ascending order (e.g., oldest-first, A→Z). */
    Asc,
    /** Descending order (e.g., newest-first, Z→A). */
    Desc
}
