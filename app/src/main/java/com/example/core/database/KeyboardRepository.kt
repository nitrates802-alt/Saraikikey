package com.example.core.database

import android.content.Context
import com.example.core.preferences.KeyboardLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class KeyboardRepository(
    private val context: Context,
    private val dao: KeyboardDao
) {
    val allLearnedWords: Flow<List<LearnedWordEntity>> = dao.observeAllLearnedWords()
    val clipboardItems: Flow<List<ClipboardEntryEntity>> = dao.observeClipboardItems()

    fun learnedWordsFor(language: KeyboardLanguage): Flow<List<LearnedWordEntity>> =
        dao.observeLearnedWordsByLanguage(language.code)

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.getBaseDictionaryCount() > 0) return@withContext
        val entries = mutableListOf<BaseDictionaryEntity>()
        entries += loadAssetDictionary("dictionaries/saraiki_base.json", KeyboardLanguage.SARAIKI.code)
        entries += loadAssetDictionary("dictionaries/urdu_base.json", KeyboardLanguage.URDU.code)
        entries += loadAssetDictionary("dictionaries/english_base.json", KeyboardLanguage.ENGLISH.code)
        if (entries.isNotEmpty()) {
            dao.insertBaseDictionaryEntries(entries)
        }
    }

    private fun loadAssetDictionary(assetPath: String, languageCode: String): List<BaseDictionaryEntity> {
        return try {
            val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val word = obj.optString("w").trim()
                    val freq = obj.optInt("f", 100)
                    if (word.isNotEmpty()) {
                        add(
                            BaseDictionaryEntity(
                                word = word,
                                language = languageCode,
                                baseFrequency = freq,
                                isInflection = false
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getAllBaseEntries(): List<BaseDictionaryEntity> = withContext(Dispatchers.IO) {
        dao.getAllBaseDictionarySync()
    }

    suspend fun getAllLearnedWords(): List<LearnedWordEntity> = withContext(Dispatchers.IO) {
        dao.getAllLearnedWordsSync()
    }

    suspend fun getAllNGrams(): List<UserNGramEntity> = withContext(Dispatchers.IO) {
        dao.getAllNGramsSync()
    }

    suspend fun recordWordAndContext(
        word: String,
        prevWord: String?,
        prev2Word: String?,
        language: KeyboardLanguage,
        isUserCoined: Boolean
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.recordLearnedWord(
            word = word,
            language = language.code,
            isUserCoined = isUserCoined,
            timestamp = now
        )
        if (!prevWord.isNullOrBlank()) {
            dao.recordNGram(
                language = language.code,
                contextPrefix = prevWord.trim(),
                nextWord = word,
                timestamp = now
            )
            if (!prev2Word.isNullOrBlank()) {
                dao.recordNGram(
                    language = language.code,
                    contextPrefix = "${prev2Word.trim()} ${prevWord.trim()}",
                    nextWord = word,
                    timestamp = now
                )
            }
        }
    }

    suspend fun forgetWord(word: String, language: KeyboardLanguage) = withContext(Dispatchers.IO) {
        dao.deleteLearnedWord(language.code, word)
        dao.deleteNGramsForWord(language.code, word)
    }

    suspend fun deleteLearnedWordById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteLearnedWordById(id)
    }

    suspend fun addCustomWord(word: String, language: KeyboardLanguage) = withContext(Dispatchers.IO) {
        val trimmed = word.trim()
        if (trimmed.isNotEmpty()) {
            dao.recordLearnedWord(trimmed, language.code, isUserCoined = true)
        }
    }

    suspend fun clearAllLearnedData() = withContext(Dispatchers.IO) {
        dao.clearAllLearnedWords()
        dao.clearAllNGrams()
    }

    suspend fun addClipboardSnippet(text: String, retentionLimit: Int) = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext
        val existing = dao.findClipboardByText(cleaned)
        if (existing != null) {
            dao.upsertClipboardItem(existing.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.upsertClipboardItem(
                ClipboardEntryEntity(
                    text = cleaned,
                    isPinned = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        dao.pruneUnpinnedClipboard(retentionLimit)
    }

    suspend fun toggleClipboardPin(id: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setClipboardPinned(id, pinned)
    }

    suspend fun deleteClipboardItem(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteClipboardItem(id)
    }

    suspend fun clearUnpinnedClipboard() = withContext(Dispatchers.IO) {
        dao.clearUnpinnedClipboard()
    }

    suspend fun clearAllClipboard() = withContext(Dispatchers.IO) {
        dao.clearAllClipboard()
    }
}
