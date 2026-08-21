package com.manhwatranslator.cache

import android.content.Context
import java.security.MessageDigest

class TranslationCache(context: Context) {
    private val dao = CacheDatabase.getInstance(context).cacheDao()

    private fun hash(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun get(sourceText: String): String? = dao.get(hash(sourceText))?.translatedText

    suspend fun put(sourceText: String, translatedText: String) {
        dao.put(TranslationCacheEntry(hash(sourceText), sourceText, translatedText))
    }
}
