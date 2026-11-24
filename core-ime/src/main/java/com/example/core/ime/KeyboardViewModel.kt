package com.example.core.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.LearnedWordEntity
import com.example.core.database.WordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Snapshot of live typing state, prefix suggestions, and learned vocabulary exposed by [KeyboardViewModel].
 */
data class KeyboardTypingUiState(
    val activeLanguage: String = KeyboardViewModel.DEFAULT_LANGUAGE,
    val composingPrefix: String = "",
    val suggestions: List<LearnedWordEntity> = emptyList(),
    val learnedWords: List<LearnedWordEntity> = emptyList(),
    val isLoadingSuggestions: Boolean = false,
    val isSavingWord: Boolean = false,
    val lastCommittedWord: String? = null,
    val errorMessage: String? = null
)

/**
 * Coordinates asynchronous word learning and real-time prefix retrieval from [WordRepository]
 * during active keyboard typing sessions inside `:core-ime`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModel(
    private val wordRepository: WordRepository,
    initialLanguage: String = DEFAULT_LANGUAGE,
    private val suggestionLimit: Int = WordRepository.DEFAULT_SUGGESTION_LIMIT
) : ViewModel() {

    private val _activeLanguage = MutableStateFlow(
        initialLanguage.trim().ifEmpty { DEFAULT_LANGUAGE }
    )
    private val _composingPrefix = MutableStateFlow("")
    private val _isLoadingSuggestions = MutableStateFlow(false)
    private val _isSavingWord = MutableStateFlow(false)
    private val _lastCommittedWord = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val suggestionsFlow: Flow<List<LearnedWordEntity>> =
        combine(_activeLanguage, _composingPrefix) { language, prefix ->
            language to prefix.trim()
        }
            .distinctUntilChanged()
            .flatMapLatest { (language, prefix) ->
                if (prefix.isEmpty()) {
                    _isLoadingSuggestions.value = false
                    flowOf(emptyList())
                } else {
                    _isLoadingSuggestions.value = true
                    wordRepository.observePrefixSuggestions(
                        language = language,
                        prefix = prefix,
                        limit = suggestionLimit
                    )
                        .onEach { _isLoadingSuggestions.value = false }
                        .catch { throwable ->
                            _isLoadingSuggestions.value = false
                            _errorMessage.value = throwable.message ?: "Failed to load suggestions"
                            emit(emptyList())
                        }
                }
            }

    private val learnedWordsFlow: Flow<List<LearnedWordEntity>> =
        _activeLanguage
            .flatMapLatest { language ->
                wordRepository.observeLearnedWords(language)
                    .catch { throwable ->
                        _errorMessage.value = throwable.message ?: "Failed to load learned words"
                        emit(emptyList())
                    }
            }

    private val transientFlagsFlow = combine(
        _isLoadingSuggestions,
        _isSavingWord,
        _lastCommittedWord,
        _errorMessage
    ) { isLoadingSuggestions, isSavingWord, lastCommittedWord, errorMessage ->
        TransientTypingFlags(
            isLoadingSuggestions = isLoadingSuggestions,
            isSavingWord = isSavingWord,
            lastCommittedWord = lastCommittedWord,
            errorMessage = errorMessage
        )
    }

    val uiState: StateFlow<KeyboardTypingUiState> = combine(
        _activeLanguage,
        _composingPrefix,
        suggestionsFlow,
        learnedWordsFlow,
        transientFlagsFlow
    ) { language, prefix, suggestions, learnedWords, flags ->
        KeyboardTypingUiState(
            activeLanguage = language,
            composingPrefix = prefix,
            suggestions = suggestions,
            learnedWords = learnedWords,
            isLoadingSuggestions = flags.isLoadingSuggestions,
            isSavingWord = flags.isSavingWord,
            lastCommittedWord = flags.lastCommittedWord,
            errorMessage = flags.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = KeyboardTypingUiState(activeLanguage = _activeLanguage.value)
    )

    fun setLanguage(language: String) {
        val normalized = language.trim()
        if (normalized.isEmpty() || normalized == _activeLanguage.value) return
        _activeLanguage.value = normalized
        _composingPrefix.value = ""
        _lastCommittedWord.value = null
    }

    fun onComposingPrefixChanged(prefix: String) {
        _composingPrefix.value = prefix
    }

    fun appendCharacter(char: Char) {
        _composingPrefix.value += char
    }

    fun appendText(text: String) {
        if (text.isEmpty()) return
        _composingPrefix.value += text
    }

    fun backspacePrefix() {
        val current = _composingPrefix.value
        if (current.isNotEmpty()) {
            _composingPrefix.value = current.dropLast(1)
        }
    }

    fun clearComposingPrefix() {
        _composingPrefix.value = ""
    }

    fun commitComposingWord(isUserCoined: Boolean = true) {
        val word = _composingPrefix.value.trim()
        if (word.isEmpty()) {
            _composingPrefix.value = ""
            return
        }
        commitTypedWord(word = word, isUserCoined = isUserCoined)
    }

    fun commitTypedWord(word: String, isUserCoined: Boolean = true) {
        val normalizedWord = word.trim()
        if (normalizedWord.isEmpty()) {
            _composingPrefix.value = ""
            return
        }
        val activeLanguage = _activeLanguage.value
        val previousWord = _lastCommittedWord.value
        _composingPrefix.value = ""
        _lastCommittedWord.value = normalizedWord

        viewModelScope.launch {
            _isSavingWord.value = true
            _errorMessage.value = null
            try {
                wordRepository.insertOrIncrementWord(
                    word = normalizedWord,
                    language = activeLanguage,
                    isUserCoined = isUserCoined,
                    previousWord = previousWord
                )
            } catch (throwable: Throwable) {
                _errorMessage.value = throwable.message ?: "Failed to save word"
            } finally {
                _isSavingWord.value = false
            }
        }
    }

    fun selectSuggestion(suggestion: LearnedWordEntity) {
        commitTypedWord(word = suggestion.word, isUserCoined = suggestion.isUserCoined)
    }

    fun deleteLearnedWord(word: String) {
        val normalizedWord = word.trim()
        if (normalizedWord.isEmpty()) return
        val activeLanguage = _activeLanguage.value
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                wordRepository.deleteWord(language = activeLanguage, word = normalizedWord)
            } catch (throwable: Throwable) {
                _errorMessage.value = throwable.message ?: "Failed to delete word"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private data class TransientTypingFlags(
        val isLoadingSuggestions: Boolean,
        val isSavingWord: Boolean,
        val lastCommittedWord: String?,
        val errorMessage: String?
    )

    companion object {
        const val DEFAULT_LANGUAGE = "srk"

        fun factory(
            wordRepository: WordRepository,
            initialLanguage: String = DEFAULT_LANGUAGE,
            suggestionLimit: Int = WordRepository.DEFAULT_SUGGESTION_LIMIT
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(KeyboardViewModel::class.java)) {
                    return KeyboardViewModel(
                        wordRepository = wordRepository,
                        initialLanguage = initialLanguage,
                        suggestionLimit = suggestionLimit
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
