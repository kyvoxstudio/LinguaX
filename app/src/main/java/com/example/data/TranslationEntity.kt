package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "Text", "Scan", "Voice"
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val translatedText: String,
    val transliteration: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
