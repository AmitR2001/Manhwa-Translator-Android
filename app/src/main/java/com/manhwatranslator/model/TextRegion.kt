package com.manhwatranslator.model

/** A detected text region on screen, in screen (not overlay) coordinates. */
data class TextRegion(
    val id: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sourceText: String,
    var translatedText: String? = null
)
