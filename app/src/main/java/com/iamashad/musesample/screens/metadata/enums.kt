package com.iamashad.musesample.screens.metadata

enum class PatientS3x(val label: String) {
    Female("Female"),
    Male("Male"),
    Other("Other")
}

enum class Posture(val label: String) {
    Standing("Standing"),
    Sitting("Sitting"),
    Supine("Supine")
}

enum class AuscPosition(val label: String) {
    Aortic("Aortic"),
    Pulmonic("Pulmonic"),
    Tricuspid("Tricuspid"),
    Mitral("Mitral")
}

enum class UnitSystem { Metric, Imperial }