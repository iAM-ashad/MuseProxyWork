package com.iamashad.musesample.model

/**
 * Immutable metadata bundled into the generated PCG PDF.
 *
 * Responsibility:
 * - Pure data holder passed to the HTML/PDF layer.
 * - Values are already formatted for display (e.g., "165 cm", "22.5").
 *
 * Notes:
 * - Optional fields default to sensible empty/label values so callers can omit them.
 * - No validation here; UI should validate before constructing this object.
 *
 * @param patientName Visible patient name on the report header.
 * @param patientId External identifier (MRN/EMR/etc.) shown next to the name.
 * @param sessionStart Human-readable timestamp, e.g. "01 Nov 2025, 10:42".
 * @param deviceModel Device string to capture acquisition source (e.g., "TAAL").
 * @param notes Free-form clinician notes. Can be blank.
 * @param age Age string for display; leave blank if unknown.
 * @param sex Display label for sex; defaults to "Other" if not specified.
 * @param height Height with units (e.g., "165 cm" or "65 in"); blank if unknown.
 * @param weight Weight with units (e.g., "68 kg" or "150 lb"); blank if unknown.
 * @param bmi Body mass index as preformatted string (e.g., "22.5"); blank if unknown.
 * @param posture Patient posture during capture (e.g., "Standing").
 * @param position Auscultation position used (e.g., "Mitral").
 */
data class PcgReportMeta(
    val patientName: String,
    val patientId: String,
    val sessionStart: String,
    val deviceModel: String,
    val notes: String,
    val age: String = "",
    val sex: String = "Other",
    val height: String = "",
    val weight: String = "",
    val bmi: String = "",
    val posture: String = "Standing",
    val position: String = "Mitral"
)
