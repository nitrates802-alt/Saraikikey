package com.example.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeKeyboardDao : KeyboardDao {

    private var nextWordId = 1L
    private var nextNGramId = 1L

    private val learnedWordsState = MutableStateFlow<List<LearnedWordEntity>>(emptyList())
    private val nGramsState = MutableStateFlow<List<UserNGramEntity>>(emptyList())
    private val clipboardState = MutableStateFlow<List<ClipboardEntryEntity>>(emptyList())
    private val baseEntries = mutableListOf<BaseDictionaryEntity>()

    var failNextInsert: Boolean = false

    override fun observeAllLearnedWords(): Flow<List<LearnedWordEntity>> =
        learnedWordsState.map { list ->
            list.sortedWith(
                compareByDescending<LearnedWordEntity> { it.lastUsedTimestamp }
                    .thenByDescending { it.frequencyCount }
            )
        }

    override fun observeLearnedWordsByLanguage(language: String): Flow<List<LearnedWordEntity>> =
        learnedWordsState.map { list ->
            list.filter { it.language == language }
                .sortedWith(
                    compareByDescending<LearnedWordEntity> { it.frequencyCount }
                        .thenByDescending { it.lastUsedTimestamp }
                )
        }

    override fun observePrefixMatches(
        language: String,
        prefix: String,
        limit: Int
    ): Flow<List<LearnedWordEntity>> =
        learnedWordsState.map { list ->
            list.filter { it.language == language && it.word.startsWith(prefix, ignoreCase = true) }
                .sortedWith(
                    compareByDescending<LearnedWordEntity> { it.frequencyCount }
                        .thenByDescending { it.lastUsedTimestamp }
                )
                .take(limit)
        }

    override suspend fun findPrefixMatches(
        language: String,
        prefix: String,
        limit: Int
    ): List<LearnedWordEntity> =
        learnedWordsState.value
            .filter { it.language == language && it.word.startsWith(prefix, ignoreCase = true) }
            .sortedWith(
                compareByDescending<LearnedWordEntity> { it.frequencyCount }
                    .thenByDescending { it.lastUsedTimestamp }
            )
            .take(limit)

    override suspend fun getAllLearnedWordsSync(): List<LearnedWordEntity> = learnedWordsState.value

    override suspend fun findLearnedWord(language: String, word: String): LearnedWordEntity? =
        learnedWordsState.value.firstOrNull { it.language == language && it.word == word }

    override suspend fun upsertLearnedWord(entity: LearnedWordEntity) {
        if (failNextInsert) {
            failNextInsert = false
            throw IllegalStateException("Simulated Room write error")
        }
        val current = learnedWordsState.value.toMutableList()
        val index = current.indexOfFirst {
            (entity.id != 0L && it.id == entity.id) ||
                (it.language == entity.language && it.word == entity.word)
        }
        val stored = if (entity.id == 0L) entity.copy(id = nextWordId++) else entity
        if (index >= 0) {
            current[index] = stored
        } else {
            current += stored
        }
        learnedWordsState.value = current
    }

    override suspend fun deleteLearnedWord(language: String, word: String) {
        learnedWordsState.value = learnedWordsState.value.filterNot {
            it.language == language && it.word == word
        }
    }

    override suspend fun deleteLearnedWordById(id: Long) {
        learnedWordsState.value = learnedWordsState.value.filterNot { it.id == id }
    }

    override suspend fun clearAllLearnedWords() {
        learnedWordsState.value = emptyList()
    }

    override suspend fun getAllNGramsSync(): List<UserNGramEntity> = nGramsState.value

    override suspend fun findNGram(
        language: String,
        contextPrefix: String,
        nextWord: String
    ): UserNGramEntity? =
        nGramsState.value.firstOrNull {
            it.language == language &&
                it.contextPrefix == contextPrefix &&
                it.nextWord == nextWord
        }

    override suspend fun upsertNGram(entity: UserNGramEntity) {
        val current = nGramsState.value.toMutableList()
        val index = current.indexOfFirst {
            (entity.id != 0L && it.id == entity.id) ||
                (it.language == entity.language &&
                    it.contextPrefix == entity.contextPrefix &&
                    it.nextWord == entity.nextWord)
        }
        val stored = if (entity.id == 0L) entity.copy(id = nextNGramId++) else entity
        if (index >= 0) {
            current[index] = stored
        } else {
            current += stored
        }
        nGramsState.value = current
    }

    override suspend fun deleteNGramsForWord(language: String, word: String) {
        nGramsState.value = nGramsState.value.filterNot {
            it.language == language && it.nextWord == word
        }
    }

    override suspend fun clearAllNGrams() {
        nGramsState.value = emptyList()
    }

    override suspend fun getBaseDictionaryCount(): Int = baseEntries.size

    override suspend fun getBaseDictionaryForLanguage(language: String): List<BaseDictionaryEntity> =
        baseEntries.filter { it.language == language }

    override suspend fun getAllBaseDictionarySync(): List<BaseDictionaryEntity> = baseEntries.toList()

    override suspend fun insertBaseDictionaryEntries(entries: List<BaseDictionaryEntity>) {
        baseEntries += entries
    }

    override fun observeClipboardItems(): Flow<List<ClipboardEntryEntity>> = clipboardState

    override suspend fun getClipboardItemsSync(): List<ClipboardEntryEntity> = clipboardState.value

    override suspend fun findClipboardByText(text: String): ClipboardEntryEntity? =
        clipboardState.value.firstOrNull { it.text == text }

    override suspend fun upsertClipboardItem(item: ClipboardEntryEntity) {
        clipboardState.value = clipboardState.value + item
    }

    override suspend fun setClipboardPinned(id: Long, pinned: Boolean) = Unit

    override suspend fun deleteClipboardItem(id: Long) = Unit

    override suspend fun clearUnpinnedClipboard() = Unit

    override suspend fun clearAllClipboard() {
        clipboardState.value = emptyList()
    }

    override suspend fun pruneUnpinnedClipboard(limit: Int) = Unit
}
