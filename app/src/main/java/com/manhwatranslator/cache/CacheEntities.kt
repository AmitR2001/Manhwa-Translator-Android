package com.manhwatranslator.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_cache")
data class TranslationCacheEntry(
    @PrimaryKey val textHash: String,
    val sourceText: String,
    val translatedText: String,
    val updatedAt: Long = System.currentTimeMillis()
)
