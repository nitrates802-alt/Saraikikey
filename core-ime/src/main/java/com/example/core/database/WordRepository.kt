package com.example.core.database

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Asynchronous repository wrapping [KeyboardDao] for real-time word insertion,
 * frequency updates, and prefix retrieval while the user types.
 */
class WordRepository(
    private val keyboardDao: KeyboardDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun observeAllLearnedWords(): Flow<List<LearnedWordEntity>> =
        keyboardDao.observeAllLearnedWords()

    fun observeLearnedWords(language: String): Flow<List<LearnedWordEntity>> {
        val normalizedLanguage = language.trim()
        if (normalizedLanguage.isEmpty()) return flowOf(emptyList())
        return keyboardDao.observeLearnedWordsByLanguage(normalizedLanguage)
    }

    fun observePrefixSuggestions(
        language: String,
        prefix: String,
        limit: Int = DEFAULT_SUGGESTION_LIMIT
    ): Flow<List<LearnedWordEntity>> {
        val normalizedLanguage = language.trim()
        val normalizedPrefix = prefix.trim()
        if (normalizedLanguage.isEmpty() || normalizedPrefix.isEmpty()) {
            return flowOf(emptyList())
        }
        return keyboardDao.observePrefixMatches(
            language = normalizedLanguage,
            prefix = normalizedPrefix,
            limit = limit.coerceAtLeast(1)
        )
    }

    suspend fun findPrefixSuggestions(
        language: String,
        prefix: String,
        limit: Int = DEFAULT_SUGGESTION_LIMIT
    ): List<LearnedWordEntity> = withContext(ioDispatcher) {
        val normalizedLanguage = language.trim()
        val normalizedPrefix = prefix.trim()
        if (normalizedLanguage.isEmpty() || normalizedPrefix.isEmpty()) {
            return@withContext emptyList()
        }
        keyboardDao.findPrefixMatches(
            language = normalizedLanguage,
            prefix = normalizedPrefix,
            limit = limit.coerceAtLeast(1)
        )
    }

    suspend fun findWord(language: String, word: String): LearnedWordEntity? =
        withContext(ioDispatcher) {
            val normalizedLanguage = language.trim()
            val normalizedWord = word.trim()
            if (normalizedLanguage.isEmpty() || normalizedWord.isEmpty()) {
                return@withContext null
            }
            keyboardDao.findLearnedWord(normalizedLanguage, normalizedWord)
        }

    suspend fun insertOrIncrementWord(
        word: String,
        language: String,
        isUserCoined: Boolean = true,
        previousWord: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(ioDispatcher) {
        val normalizedWord = word.trim()
        val normalizedLanguage = language.trim()
        if (normalizedWord.isEmpty() || normalizedLanguage.isEmpty()) {
            return@withContext
        }
        keyboardDao.recordLearnedWord(
            word = normalizedWord,
            language = normalizedLanguage,
            isUserCoined = isUserCoined,
            timestamp = timestamp
        )
        val normalizedPrev = previousWord?.trim().orEmpty()
        if (normalizedPrev.isNotEmpty()) {
            keyboardDao.recordNGram(
                language = normalizedLanguage,
                contextPrefix = normalizedPrev,
                nextWord = normalizedWord,
                timestamp = timestamp
            )
        }
    }

    suspend fun upsertWord(entity: LearnedWordEntity) = withContext(ioDispatcher) {
        val normalizedWord = entity.word.trim()
        val normalizedLanguage = entity.language.trim()
        if (normalizedWord.isEmpty() || normalizedLanguage.isEmpty()) {
            return@withContext
        }
        keyboardDao.upsertLearnedWord(
            entity.copy(
                word = normalizedWord,
                language = normalizedLanguage
            )
        )
    }

    suspend fun deleteWord(language: String, word: String) = withContext(ioDispatcher) {
        val normalizedLanguage = language.trim()
        val normalizedWord = word.trim()
        if (normalizedLanguage.isEmpty() || normalizedWord.isEmpty()) {
            return@withContext
        }
        keyboardDao.deleteLearnedWord(normalizedLanguage, normalizedWord)
        keyboardDao.deleteNGramsForWord(normalizedLanguage, normalizedWord)
    }

    suspend fun clearAllLearnedWords() = withContext(ioDispatcher) {
        keyboardDao.clearAllLearnedWords()
        keyboardDao.clearAllNGrams()
    }

    companion object {
        const val DEFAULT_SUGGESTION_LIMIT = 10

        fun fromDatabase(database: SaraikiKeyboardDatabase): WordRepository =
            WordRepository(database.keyboardDao())
    }
}
