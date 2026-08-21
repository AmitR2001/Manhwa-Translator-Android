package com.manhwatranslator.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Explicitly downloads the Korean OCR and KO->EN translation models. Both throwaway
 * clients used just for triggering the download are closed immediately after, so this
 * doesn't itself leak sessions.
 */
object ModelManager {

    data class Result(val ocrReady: Boolean, val translateReady: Boolean, val error: String?)

    suspend fun downloadKoreanModels(): Result {
        var ocrOk = false
        var translateOk = false
        var lastError: String? = null

        runCatching { downloadOcrModel() }
            .onSuccess { ocrOk = true }
            .onFailure { lastError = "OCR: ${it.message}" }

        runCatching { downloadTranslateModel() }
            .onSuccess { translateOk = true }
            .onFailure { lastError = "Translate: ${it.message}" }

        return Result(ocrOk, translateOk, lastError)
    }

    private suspend fun downloadOcrModel() {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            val blank = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val image = InputImage.fromBitmap(blank, 0)
            recognizer.process(image).await()
        } finally {
            recognizer.close()
        }
    }

    private suspend fun downloadTranslateModel() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.KOREAN)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
        } finally {
            translator.close()
        }
    }
}
