package com.example.feature.settings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SaraikiProApplication
import com.example.core.ai.AiTaskType
import com.example.core.ai.PlaceholderAiRepository
import com.example.core.database.LearnedWordEntity
import com.example.core.ime.SaraikiKeyboardService
import com.example.core.linguistics.DictionaryIoManager
import com.example.core.preferences.CustomThemeConfig
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardLanguage
import com.example.core.preferences.KeyboardSettings
import com.example.core.preferences.KeyboardThemePreset
import com.example.core.preferences.SoundProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ImeStatus(
    val isEnabledInSystem: Boolean = false,
    val isSelectedAsDefault: Boolean = false
)

data class AiAssistantUiState(
    val isLoading: Boolean = false,
    val lastTaskTitle: String? = null,
    val outputText: String = "",
    val notice: String = PlaceholderAiRepository.NOTICE_FIREBASE_REQUIRED
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as SaraikiProApplication).container
    private val settingsRepo = container.settingsRepository
    private val keyboardRepo = container.keyboardRepository
    private val linguisticEngine = container.linguisticEngine
    private val soundHapticEngine = container.soundHapticEngine
    private val aiRepository = container.aiRepository

    val settings: StateFlow<KeyboardSettings> = settingsRepo.settingsFlow

    private val _imeStatus = MutableStateFlow(ImeStatus())
    val imeStatus: StateFlow<ImeStatus> = _imeStatus.asStateFlow()

    private val _dictFilterLang = MutableStateFlow<KeyboardLanguage?>(null)
    val dictFilterLang: StateFlow<KeyboardLanguage?> = _dictFilterLang.asStateFlow()

    private val _dictSearchQuery = MutableStateFlow("")
    val dictSearchQuery: StateFlow<String> = _dictSearchQuery.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _aiState = MutableStateFlow(AiAssistantUiState())
    val aiState: StateFlow<AiAssistantUiState> = _aiState.asStateFlow()

    val filteredLearnedWords: StateFlow<List<LearnedWordEntity>> = combine(
        keyboardRepo.allLearnedWords,
        _dictFilterLang,
        _dictSearchQuery
    ) { all, lang, query ->
        all.filter { item ->
            (lang == null || item.language == lang.code) &&
                (query.isBlank() || item.word.contains(query.trim(), ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshImeStatus()
    }

    fun refreshImeStatus() {
        val ctx = getApplication<Application>()
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val targetComponent = ComponentName(ctx, SaraikiKeyboardService::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.component == targetComponent } == true
        val currentDefault = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (_: Exception) {
            null
        }
        val selected = currentDefault == targetComponent.flattenToShortString() ||
            currentDefault == targetComponent.flattenToString()
        _imeStatus.value = ImeStatus(isEnabledInSystem = enabled, isSelectedAsDefault = selected)
    }

    fun showInputMethodPicker() {
        val imm = getApplication<Application>().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        viewModelScope.launch { settingsRepo.updateSettings(transform) }
    }

    fun toggleLanguageEnabled(language: KeyboardLanguage, enabled: Boolean) {
        updateSettings { current ->
            val updated = if (enabled) {
                (current.enabledLanguages + language).distinct()
            } else {
                val filtered = current.enabledLanguages.filter { it != language }
                filtered.ifEmpty { listOf(language) }
            }
            val active = if (current.activeLanguage in updated) current.activeLanguage else updated.first()
            current.copy(enabledLanguages = updated, activeLanguage = active)
        }
    }

    fun moveLanguageOrder(language: KeyboardLanguage, moveUp: Boolean) {
        updateSettings { current ->
            val list = current.enabledLanguages.toMutableList()
            val idx = list.indexOf(language)
            if (idx < 0) return@updateSettings current
            val targetIdx = if (moveUp) idx - 1 else idx + 1
            if (targetIdx !in list.indices) return@updateSettings current
            list.removeAt(idx)
            list.add(targetIdx, language)
            current.copy(enabledLanguages = list)
        }
    }

    fun selectThemePreset(preset: KeyboardThemePreset) {
        updateSettings { it.copy(themePreset = preset) }
    }

    fun updateCustomTheme(transform: (CustomThemeConfig) -> CustomThemeConfig) {
        updateSettings { s ->
            s.copy(
                themePreset = KeyboardThemePreset.CUSTOM,
                customTheme = transform(s.customTheme)
            )
        }
    }

    fun setErgonomicMode(mode: ErgonomicMode) {
        updateSettings { it.copy(ergonomicMode = mode) }
    }

    fun previewSound(profile: SoundProfile, volume: Float) {
        soundHapticEngine.previewSound(profile, volume)
    }

    fun previewHaptic(durationMs: Int) {
        soundHapticEngine.previewHaptic(durationMs)
    }

    fun setDictionaryFilter(lang: KeyboardLanguage?) {
        _dictFilterLang.value = lang
    }

    fun setDictionarySearch(query: String) {
        _dictSearchQuery.value = query
    }

    fun addCustomWord(word: String, language: KeyboardLanguage) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            keyboardRepo.addCustomWord(clean, language)
            linguisticEngine.hydrateBlocking()
            _statusBanner.value = "Added \"$clean\" to ${language.englishName} dictionary"
        }
    }

    fun deleteLearnedWord(item: LearnedWordEntity) {
        viewModelScope.launch {
            keyboardRepo.deleteLearnedWordById(item.id)
            linguisticEngine.forgetLearnedWord(item.word, KeyboardLanguage.fromCode(item.language))
        }
    }

    fun clearAllLearnedHistory() {
        viewModelScope.launch {
            keyboardRepo.clearAllLearnedData()
            keyboardRepo.clearAllClipboard()
            linguisticEngine.clearAllLearnedInMemory()
            _statusBanner.value = "All learned words, N-grams, and clipboard history deleted"
        }
    }

    suspend fun exportDictionaryText(asCsv: Boolean): String {
        val words = keyboardRepo.getAllLearnedWords()
        return if (asCsv) DictionaryIoManager.exportAsCsv(words) else DictionaryIoManager.exportAsJson(words)
    }

    fun importDictionaryText(raw: String, fallbackLanguage: KeyboardLanguage) {
        viewModelScope.launch {
            try {
                val parsed = DictionaryIoManager.importFromText(raw, fallbackLanguage)
                parsed.forEach { (w, lang) ->
                    keyboardRepo.addCustomWord(w, lang)
                }
                linguisticEngine.hydrateBlocking()
                _statusBanner.value = "Imported ${parsed.size} words locally"
            } catch (_: Exception) {
                _statusBanner.value = "Could not parse dictionary file"
            }
        }
    }

    suspend fun exportFullBackupJson(): String {
        val settingsJson = settingsRepo.exportSettingsJson()
        val wordsJson = DictionaryIoManager.exportAsJson(keyboardRepo.getAllLearnedWords())
        return JSONObject().apply {
            put("settings", JSONObject(settingsJson))
            put("learned_words", org.json.JSONArray(wordsJson))
        }.toString(2)
    }

    fun importFullBackupJson(raw: String) {
        viewModelScope.launch {
            try {
                val root = JSONObject(raw)
                root.optJSONObject("settings")?.let { settingsObj ->
                    settingsRepo.importSettingsJson(settingsObj.toString())
                }
                root.optJSONArray("learned_words")?.let { arr ->
                    val imported = DictionaryIoManager.importFromText(arr.toString(), KeyboardLanguage.SARAIKI)
                    imported.forEach { (w, lang) -> keyboardRepo.addCustomWord(w, lang) }
                }
                linguisticEngine.hydrateBlocking()
                _statusBanner.value = "Backup restored successfully"
            } catch (_: Exception) {
                _statusBanner.value = "Invalid backup JSON file"
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun runAiTask(task: AiTaskType, inputText: String, targetLanguage: KeyboardLanguage) {
        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isLoading = true)
            val result = aiRepository.runTask(task, inputText, targetLanguage)
            _aiState.value = AiAssistantUiState(
                isLoading = false,
                lastTaskTitle = "${task.title} (${targetLanguage.englishName})",
                outputText = result.outputText,
                notice = result.notice
            )
        }
    }
}
