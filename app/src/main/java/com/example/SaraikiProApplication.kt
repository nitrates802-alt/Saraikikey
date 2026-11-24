package com.example

import android.app.Application
import android.content.Context
import com.example.core.ai.AiRepository
import com.example.core.ai.PlaceholderAiRepository
import com.example.core.database.KeyboardRepository
import com.example.core.database.SaraikiKeyboardDatabase
import com.example.core.database.WordRepository
import com.example.core.ime.ImeEngineDelegate
import com.example.core.ime.ImeHostApplication
import com.example.core.ime.SaraikiImeDelegate
import com.example.core.ime.SoundHapticEngine
import com.example.core.linguistics.LinguisticEngine
import com.example.core.linguistics.SaraikiCharacterConfig
import com.example.core.preferences.SettingsRepository

class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val database = SaraikiKeyboardDatabase.getInstance(context)
    val keyboardRepository = KeyboardRepository(context, database.keyboardDao())
    val wordRepository = WordRepository(database.keyboardDao())
    val saraikiConfig = SaraikiCharacterConfig.fromAssets(context)
    val linguisticEngine = LinguisticEngine(keyboardRepository, saraikiConfig)
    val soundHapticEngine = SoundHapticEngine(context)
    val aiRepository: AiRepository = PlaceholderAiRepository()

    init {
        linguisticEngine.hydrateAsync()
        soundHapticEngine.initializeAsync()
    }
}

class SaraikiProApplication : Application(), ImeHostApplication {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun createImeEngineDelegate(): ImeEngineDelegate = SaraikiImeDelegate(container)
}
