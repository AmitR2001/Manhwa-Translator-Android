package com.manhwatranslator.detection

import android.graphics.Bitmap
import com.manhwatranslator.model.TextRegion

interface TextDetector {
    suspend fun detect(bitmap: Bitmap): List<TextRegion>

    /** Releases any underlying native resources. Call when this detector is no longer
     * needed (e.g. session stop) - leaving it unclosed across repeated create/stop cycles
     * can degrade or break OCR until the process restarts. */
    fun close() {}
}
