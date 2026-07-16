package com.example.data

import kotlinx.coroutines.flow.Flow

class TranslationRepository(private val dao: TranslationDao) {
    val allTranslations: Flow<List<TranslationEntity>> = dao.getAllTranslations()

    fun getTranslationById(id: Int): Flow<TranslationEntity?> = dao.getTranslationById(id)

    suspend fun insertTranslation(translation: TranslationEntity): Long =
        dao.insertTranslation(translation)

    suspend fun updateTranslation(translation: TranslationEntity) =
        dao.updateTranslation(translation)

    suspend fun updateFavorite(id: Int, isFavorite: Boolean) =
        dao.updateFavorite(id, isFavorite)

    suspend fun deleteTranslation(translation: TranslationEntity) =
        dao.deleteTranslation(translation)

    suspend fun deleteTranslationById(id: Int) =
        dao.deleteTranslationById(id)

    suspend fun clearHistory() =
        dao.clearHistory()
}
