package com.iamashad.musesample.model

/**
 * Domain model for a recorded PCG session used across UI and business layers.
 *
 * Notes:
 * - This is NOT a Room entity. See mappers in `SessionRepository` to convert to/from
 *   `SessionEntity` (DB row). Keeping this model decoupled avoids Room leaking into UI.
 * - Many fields are already user-formatted (e.g., `"165 cm"`, `"22.5"`).
 * - `pdfPath` is nullable because the report may be generated later from the session card.
 *
 * Identity:
 * - [id] defaults to `System.currentTimeMillis()` when constructed client-side.
 *   The same value is saved as the primary key in the database.
 *
 * Files:
 * - [wavPath] is the local path to the captured audio (raw or prefiltered, whichever was chosen).
 * - [pdfPath] is the local path to the generated PDF report, if any.
 *
 * Display:
 * - [sessionStart] is a human-readable string (e.g., "01 Nov 2025, 10:42").
 *   The DB also stores a separate epoch millis for sorting.
 */
data class Session(
    val id: Long = System.currentTimeMillis(),
    val patientName: String,
    val patientId: String,
    val age: String,
    val sex: String,
    val height: String,
    val weight: String,
    val bmi: String,
    val sessionStart: String,
    val deviceModel: String,
    val notes: String,
    val posture: String = "Standing",
    val position: String = "Mitral",
    val wavPath: String,
    val rawWavPath: String,
    val pdfPath: String? = null
)
