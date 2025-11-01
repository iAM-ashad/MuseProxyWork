package com.iamashad.musesample.screens.session

enum class PdfFilter(val label: String) {
    All("All"),
    With("With PDF"),
    Without("No PDF")
}

enum class TimeFilter { All, Last7, Last30 }
enum class SortKey(val label: String) {
    Date("Date"), Name("Name"), Position("Position"), Pdf(
        "Has PDF"
    )
}

enum class SortOrder { Asc, Desc }