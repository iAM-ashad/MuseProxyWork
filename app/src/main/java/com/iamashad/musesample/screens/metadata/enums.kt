package com.iamashad.musesample.screens.metadata

/**
 * Patient-reported sex options shown in the metadata form.
 * Stored as a display label string inside the saved session.
 */

enum class PatientS3x(val label: String) {
    Female("Female"),
    Male("Male"),
    Other("Other")
}

/**
 * Patient posture during auscultation.
 * Saved to the session and printed in the PDF.
 */
enum class Posture(val label: String) {
    Standing("Standing"),
    Sitting("Sitting"),
    Supine("Supine")
}

/**
 * Standard anterior auscultation positions.
 * Used both for tagging sessions and filtering in history.
 */
enum class AuscPosition(val label: String) {
    Aortic("Aortic"),
    Pulmonic("Pulmonic"),
    Tricuspid("Tricuspid"),
    Mitral("Mitral")
}

/**
 * Unit system toggle for height/weight input.
 * The UI formats values (e.g., "165 cm", "150 lb") before saving.
 */
enum class UnitSystem { Metric, Imperial }
