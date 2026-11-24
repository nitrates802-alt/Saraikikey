package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardDao {

    // --- Learned Words ---
    @Query("SELECT * FROM learned_words ORDER BY last_used_timestamp DESC, frequency_count DESC")
    fun observeAllLearnedWords(): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words WHERE language = :language ORDER BY frequency_count DESC, last_used_timestamp DESC")
    fun observeLearnedWordsByLanguage(language: String): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words WHERE language = :language AND word LIKE :prefix || '%' ORDER BY frequency_count DESC, last_used_timestamp DESC LIMIT :limit")
    fun observePrefixMatches(language: String, prefix: String, limit: Int = 10): Flow<List<LearnedWordEntity>>

    @Query("SELECT * FROM learned_words WHERE language = :language AND word LIKE :prefix || '%' ORDER BY frequency_count DESC, last_used_timestamp DESC LIMIT :limit")
    suspend fun findPrefixMatches(language: String, prefix: String, limit: Int = 10): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words")
    suspend fun getAllLearnedWordsSync(): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words WHERE language = :language AND word = :word LIMIT 1")
    suspend fun findLearnedWord(language: String, word: String): LearnedWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearnedWord(entity: LearnedWordEntity)

    @Transaction
    suspend fun recordLearnedWord(
        word: String,
        language: String,
        isUserCoined: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val existing = findLearnedWord(language, word)
        if (existing != null) {
            upsertLearnedWord(
                existing.copy(
                    frequencyCount = existing.frequencyCount + 1,
                    lastUsedTimestamp = timestamp,
                    isUserCoined = existing.isUserCoined || isUserCoined
                )
            )
        } else {
            upsertLearnedWord(
                LearnedWordEntity(
                    word = word,
                    language = language,
                    frequencyCount = 1,
                    lastUsedTimestamp = timestamp,
                    isUserCoined = isUserCoined
                )
            )
        }
    }

    @Query("DELETE FROM learned_words WHERE language = :language AND word = :word")
    suspend fun deleteLearnedWord(language: String, word: String)

    @Query("DELETE FROM learned_words WHERE id = :id")
    suspend fun deleteLearnedWordById(id: Long)

    @Query("DELETE FROM learned_words")
    suspend fun clearAllLearnedWords()

    // --- User N-Grams ---
    @Query("SELECT * FROM user_ngrams")
    suspend fun getAllNGramsSync(): List<UserNGramEntity>

    @Query("SELECT * FROM user_ngrams WHERE language = :language AND context_prefix = :contextPrefix AND next_word = :nextWord LIMIT 1")
    suspend fun findNGram(language: String, contextPrefix: String, nextWord: String): UserNGramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNGram(entity: UserNGramEntity)

    @Transaction
    suspend fun recordNGram(
        language: String,
        contextPrefix: String,
        nextWord: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val existing = findNGram(language, contextPrefix, nextWord)
        if (existing != null) {
            upsertNGram(
                existing.copy(
                    transitionCount = existing.transitionCount + 1,
                    lastUsedTimestamp = timestamp
                )
            )
        } else {
            upsertNGram(
                UserNGramEntity(
                    language = language,
                    contextPrefix = contextPrefix,
                    nextWord = nextWord,
                    transitionCount = 1,
                    lastUsedTimestamp = timestamp
                )
            )
        }
    }

    @Query("DELETE FROM user_ngrams WHERE language = :language AND next_word = :word")
    suspend fun deleteNGramsForWord(language: String, word: String)

    @Query("DELETE FROM user_ngrams")
    suspend fun clearAllNGrams()

    // --- Base Dictionaries ---
    @Query("SELECT COUNT(*) FROM base_dictionary")
    suspend fun getBaseDictionaryCount(): Int

    @Query("SELECT * FROM base_dictionary WHERE language = :language")
    suspend fun getBaseDictionaryForLanguage(language: String): List<BaseDictionaryEntity>

    @Query("SELECT * FROM base_dictionary")
    suspend fun getAllBaseDictionarySync(): List<BaseDictionaryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBaseDictionaryEntries(entries: List<BaseDictionaryEntity>)

    // --- Clipboard ---
    @Query("SELECT * FROM clipboard_items ORDER BY is_pinned DESC, created_at DESC")
    fun observeClipboardItems(): Flow<List<ClipboardEntryEntity>>

    @Query("SELECT * FROM clipboard_items ORDER BY is_pinned DESC, created_at DESC")
    suspend fun getClipboardItemsSync(): List<ClipboardEntryEntity>

    @Query("SELECT * FROM clipboard_items WHERE text = :text LIMIT 1")
    suspend fun findClipboardByText(text: String): ClipboardEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClipboardItem(item: ClipboardEntryEntity)

    @Query("UPDATE clipboard_items SET is_pinned = :pinned WHERE id = :id")
    suspend fun setClipboardPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteClipboardItem(id: Long)

    @Query("DELETE FROM clipboard_items WHERE is_pinned = 0")
    suspend fun clearUnpinnedClipboard()

    @Query("DELETE FROM clipboard_items")
    suspend fun clearAllClipboard()

    @Query("DELETE FROM clipboard_items WHERE is_pinned = 0 AND id NOT IN (SELECT id FROM clipboard_items WHERE is_pinned = 0 ORDER BY created_at DESC LIMIT :limit)")
    suspend fun pruneUnpinnedClipboard(limit: Int)
}
