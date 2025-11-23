package com.iamashad.musesample.screens.session

/**
 * Filter for whether a session already has a generated PDF.
 */
enum class PdfFilter(val label: String) {
    All("All"),
    With("With PDF"),
    Without("No PDF")
}

/**
 * Time-window filter applied against the session's display date.
 */
enum class TimeFilter {
    All,
    Last7,
    Last30
}

/**
 * Sort keys available for the session list.
 * Simplified for v2: we care about date and PDF status.
 */
enum class SortKey(val label: String) {
    Date("Date"),
    Pdf("Has PDF")
}

/**
 * Sort direction applied to the chosen [SortKey].
 */
enum class SortOrder {
    Asc,
    Desc
}
