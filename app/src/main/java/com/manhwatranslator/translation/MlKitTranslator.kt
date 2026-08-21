package com.manhwatranslator.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.tasks.await

/** V1 baseline: on-device ML Kit KO->EN translation model. */
class MlKitTranslator : Translator {

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.KOREAN)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()

    private val client = Translation.getClient(options)

    override suspend fun ensureReady() {
        val conditions = DownloadConditions.Builder().build()
        client.downloadModelIfNeeded(conditions).await()
    }

    override suspend fun translate(koreanText: String): String {
        return client.translate(koreanText).await()
    }

    override fun close() {
        client.close()
    }
}
