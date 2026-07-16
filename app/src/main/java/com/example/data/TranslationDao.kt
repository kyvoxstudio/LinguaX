package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY timestamp DESC")
    fun getAllTranslations(): Flow<List<TranslationEntity>>

    @Query("SELECT * FROM translations WHERE id = :id LIMIT 1")
    fun getTranslationById(id: Int): Flow<TranslationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslationEntity): Long

    @Update
    suspend fun updateTranslation(translation: TranslationEntity)

    @Query("UPDATE translations SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)

    @Delete
    suspend fun deleteTranslation(translation: TranslationEntity)

    @Query("DELETE FROM translations WHERE id = :id")
    suspend fun deleteTranslationById(id: Int)

    @Query("DELETE FROM translations")
    suspend fun clearHistory()
}
