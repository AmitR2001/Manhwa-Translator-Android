package com.manhwatranslator.pipeline

/**
 * In-memory tuning values for tap-to-translate mode. Placeholder for a future in-app
 * Settings screen - change these constants for now; wire to real UI controls later.
 */
object PipelineSettings {
    /** Default box size (cm) for a plain tap (no drag). */
    var defaultCropWidthCm: Float = 6.5f
    var defaultCropHeightCm: Float = 3.0f

    /** Height (cm) of the tappable band centered vertically on screen. */
    var tapBandHeightCm: Float = 8.0f
}
