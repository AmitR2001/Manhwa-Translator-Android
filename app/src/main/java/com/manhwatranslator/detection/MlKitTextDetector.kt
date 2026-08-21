package com.manhwatranslator.detection

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.manhwatranslator.model.TextRegion
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Returns one TextRegion per ML Kit BLOCK (its own paragraph/speech-bubble grouping),
 * not per line - this keeps naturally-related lines (a multi-line dialogue) together as
 * one unit, and keeps unrelated nearby content (a watermark, an ad, another panel) in a
 * separate block instead of blending them. Lines without Korean (Hangul) characters are
 * dropped; a block left with no Hangul lines at all is dropped entirely.
 */
class MlKitTextDetector : TextDetector {

    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    override suspend fun detect(bitmap: Bitmap): List<TextRegion> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val hangulLines = block.lines.mapNotNull { line ->
                val text = line.text.trim()
                if (text.isNotEmpty() && containsHangul(text)) text else null
            }
            if (hangulLines.isEmpty()) return@mapNotNull null

            TextRegion(
                id = UUID.randomUUID().toString(),
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom,
                sourceText = hangulLines.joinToString(" ")
            )
        }
    }

    override fun close() {
        recognizer.close()
    }

    private fun containsHangul(text: String): Boolean {
        return text.any { ch ->
            (ch in '\uAC00'..'\uD7A3') ||
            (ch in '\u1100'..'\u11FF') ||
            (ch in '\u3130'..'\u318F')
        }
    }
}
