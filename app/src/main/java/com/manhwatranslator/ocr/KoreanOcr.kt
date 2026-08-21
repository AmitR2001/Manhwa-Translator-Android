package com.manhwatranslator.ocr

import android.graphics.Bitmap
import com.manhwatranslator.model.TextRegion

/**
 * Stage kept separate from TextDetector for V2, when detection (bounding boxes)
 * and recognition (characters) become distinct models (e.g. custom detector + custom OCR).
 * In V1 this is a passthrough since MlKitTextDetector already returns recognized text.
 */
interface KoreanOcr {
    suspend fun recognize(bitmap: Bitmap, region: TextRegion): String
}

class PassthroughOcr : KoreanOcr {
    override suspend fun recognize(bitmap: Bitmap, region: TextRegion): String = region.sourceText
}
