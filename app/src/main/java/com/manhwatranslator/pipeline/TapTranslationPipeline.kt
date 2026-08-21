package com.manhwatranslator.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.manhwatranslator.cache.TranslationCache
import com.manhwatranslator.detection.MlKitTextDetector
import com.manhwatranslator.detection.TextDetector
import com.manhwatranslator.model.TextRegion
import com.manhwatranslator.overlay.BottomTranslationPanel
import com.manhwatranslator.translation.MlKitTranslator
import com.manhwatranslator.translation.Translator
import kotlinx.coroutines.delay

/**
 * On-demand pipeline: given ONE cropped region (around a user's tap), detects text
 * blocks within it and keeps the ones closest to the crop's center - normally just one,
 * but includes a second/third block too if they're comparably close (within 1.5x the
 * nearest block's distance), since ML Kit sometimes splits one dialogue into adjacent
 * blocks. Translates the kept blocks together, in reading order (top to bottom).
 */
class TapTranslationPipeline(context: Context) {

    private val detector: TextDetector = MlKitTextDetector()
    private val translator: Translator = MlKitTranslator()
    private val cache = TranslationCache(context)
    val bottomPanel = BottomTranslationPanel(context)

    private val maxAttempts = 3
    private val retryDelayMs = 250L

    suspend fun ensureReady() {
        translator.ensureReady()
        bottomPanel.show()
    }

    suspend fun translateCrop(crop: Bitmap): String? {
        var regions = emptyList<TextRegion>()
        for (attempt in 1..maxAttempts) {
            regions = detector.detect(crop)
            if (regions.isNotEmpty()) break
            if (attempt < maxAttempts) delay(retryDelayMs)
        }
        if (regions.isEmpty()) return null

        val relevant = pickRelevantBlocks(regions, crop.width, crop.height)
        if (relevant.isEmpty()) return null

        val translatedLines = relevant.map { region ->
            val cached = cache.get(region.sourceText)
            val translated = cached ?: translator.translate(region.sourceText).also {
                cache.put(region.sourceText, it)
            }
            bottomPanel.addLine(translated)
            translated
        }
        return translatedLines.joinToString(" ")
    }

    private fun pickRelevantBlocks(regions: List<TextRegion>, cropWidth: Int, cropHeight: Int): List<TextRegion> {
        if (regions.size <= 1) return regions
        val centerX = cropWidth / 2
        val centerY = cropHeight / 2

        val withDistance = regions.map { r ->
            val cx = (r.left + r.right) / 2
            val cy = (r.top + r.bottom) / 2
            val dx = (cx - centerX).toDouble()
            val dy = (cy - centerY).toDouble()
            r to (dx * dx + dy * dy)
        }
        val minDist = withDistance.minOf { it.second }
        val threshold = minDist * 2.25 // ~1.5x linear distance from the nearest block
        return withDistance.filter { it.second <= threshold }
            .map { it.first }
            .sortedBy { it.top }
    }

    fun stop() {
        detector.close()
        translator.close()
        bottomPanel.hide()
    }
}
