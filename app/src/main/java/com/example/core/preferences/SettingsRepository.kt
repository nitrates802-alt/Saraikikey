package com.example.core.preferences

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject

private val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "saraiki_pro_keyboard_prefs"
)

class SettingsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val ENABLED_LANGUAGES = stringPreferencesKey("enabled_languages")
        val ACTIVE_LANGUAGE = stringPreferencesKey("active_language")
        val MULTILINGUAL_TYPING = booleanPreferencesKey("multilingual_typing")
        val SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        val LONG_PRESS_DELAY = intPreferencesKey("long_press_delay_ms")
        val SHOW_KEYPRESS_PREVIEW = booleanPreferencesKey("show_keypress_preview")
        val AUTO_CAPITALIZATION = booleanPreferencesKey("auto_capitalization")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val SMART_PUNCTUATION = booleanPreferencesKey("smart_punctuation")
        val LEARN_FROM_TYPED_WORDS = booleanPreferencesKey("learn_from_typed_words")
        val AUTOCORRECT_SARAIKI = booleanPreferencesKey("autocorrect_saraiki")
        val AUTOCORRECT_URDU = booleanPreferencesKey("autocorrect_urdu")
        val AUTOCORRECT_ENGLISH = booleanPreferencesKey("autocorrect_english")
        val NEXT_WORD_PREDICTION = booleanPreferencesKey("next_word_prediction")
        val GLIDE_TYPING = booleanPreferencesKey("glide_typing_enabled")
        val SHOW_GLIDE_TRAIL = booleanPreferencesKey("show_glide_trail")
        val SPACEBAR_TRACKPAD = booleanPreferencesKey("spacebar_trackpad")
        val GESTURE_DELETE = booleanPreferencesKey("gesture_delete")
        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIPBOARD_LIMIT = intPreferencesKey("clipboard_retention_limit")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val SOUND_PROFILE = stringPreferencesKey("sound_profile")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_DURATION = intPreferencesKey("haptic_duration_ms")
        val HEIGHT_PERCENT = intPreferencesKey("height_percent")
        val WIDTH_PERCENT = intPreferencesKey("width_percent")
        val ERGONOMIC_MODE = stringPreferencesKey("ergonomic_mode")
        val FLOATING_X = intPreferencesKey("floating_x")
        val FLOATING_Y = intPreferencesKey("floating_y")
        val FLOATING_ALPHA = floatPreferencesKey("floating_alpha")
        val THEME_PRESET = stringPreferencesKey("theme_preset")

        val CUSTOM_BG = intPreferencesKey("custom_bg")
        val CUSTOM_PRIMARY_KEY = intPreferencesKey("custom_primary_key")
        val CUSTOM_FUNC_KEY = intPreferencesKey("custom_func_key")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent")
        val CUSTOM_TEXT = intPreferencesKey("custom_text")
        val CUSTOM_HINT = intPreferencesKey("custom_hint")
        val CUSTOM_WALLPAPER_URI = stringPreferencesKey("custom_wallpaper_uri")
        val CUSTOM_WALLPAPER_DIM = floatPreferencesKey("custom_wallpaper_dim")
        val CUSTOM_WALLPAPER_BLUR = floatPreferencesKey("custom_wallpaper_blur")
        val CUSTOM_KEY_OPACITY = floatPreferencesKey("custom_key_opacity")
        val CUSTOM_KEY_BORDERS = booleanPreferencesKey("custom_key_borders")
        val CUSTOM_CORNER_RADIUS = floatPreferencesKey("custom_corner_radius")
        val CUSTOM_FONT_SIZE = floatPreferencesKey("custom_font_size")
        val CUSTOM_SECONDARY_HINTS = booleanPreferencesKey("custom_secondary_hints")
    }

    val settingsFlow: StateFlow<KeyboardSettings> = context.keyboardDataStore.data
        .map { prefs -> mapPreferences(prefs) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = KeyboardSettings()
        )

    private fun mapPreferences(prefs: Preferences): KeyboardSettings {
        val enabledRaw = prefs[Keys.ENABLED_LANGUAGES]
        val enabled = if (enabledRaw.isNullOrBlank()) {
            listOf(KeyboardLanguage.SARAIKI, KeyboardLanguage.URDU, KeyboardLanguage.ENGLISH)
        } else {
            enabledRaw.split(",")
                .mapNotNull { code -> KeyboardLanguage.entries.firstOrNull { it.code == code.trim() } }
                .ifEmpty { listOf(KeyboardLanguage.SARAIKI) }
        }
        val active = KeyboardLanguage.fromCode(prefs[Keys.ACTIVE_LANGUAGE])
            .let { if (it in enabled) it else enabled.first() }

        val customDefaults = CustomThemeConfig()
        val customTheme = CustomThemeConfig(
            backgroundColor = prefs[Keys.CUSTOM_BG] ?: customDefaults.backgroundColor,
            primaryKeyColor = prefs[Keys.CUSTOM_PRIMARY_KEY] ?: customDefaults.primaryKeyColor,
            functionKeyColor = prefs[Keys.CUSTOM_FUNC_KEY] ?: customDefaults.functionKeyColor,
            accentColor = prefs[Keys.CUSTOM_ACCENT] ?: customDefaults.accentColor,
            primaryTextColor = prefs[Keys.CUSTOM_TEXT] ?: customDefaults.primaryTextColor,
            hintTextColor = prefs[Keys.CUSTOM_HINT] ?: customDefaults.hintTextColor,
            wallpaperUri = prefs[Keys.CUSTOM_WALLPAPER_URI]?.takeIf { it.isNotBlank() },
            wallpaperDim = prefs[Keys.CUSTOM_WALLPAPER_DIM] ?: customDefaults.wallpaperDim,
            wallpaperBlur = prefs[Keys.CUSTOM_WALLPAPER_BLUR] ?: customDefaults.wallpaperBlur,
            keyOpacity = prefs[Keys.CUSTOM_KEY_OPACITY] ?: customDefaults.keyOpacity,
            showKeyBorders = prefs[Keys.CUSTOM_KEY_BORDERS] ?: customDefaults.showKeyBorders,
            cornerRadiusDp = prefs[Keys.CUSTOM_CORNER_RADIUS] ?: customDefaults.cornerRadiusDp,
            labelFontSizeSp = prefs[Keys.CUSTOM_FONT_SIZE] ?: customDefaults.labelFontSizeSp,
            showSecondaryHints = prefs[Keys.CUSTOM_SECONDARY_HINTS] ?: customDefaults.showSecondaryHints
        )

        return KeyboardSettings(
            enabledLanguages = enabled,
            activeLanguage = active,
            multilingualTyping = prefs[Keys.MULTILINGUAL_TYPING] ?: true,
            showNumberRow = prefs[Keys.SHOW_NUMBER_ROW] ?: false,
            longPressDelayMs = (prefs[Keys.LONG_PRESS_DELAY] ?: 280).coerceIn(150, 600),
            showKeypressPreview = prefs[Keys.SHOW_KEYPRESS_PREVIEW] ?: true,
            autoCapitalization = prefs[Keys.AUTO_CAPITALIZATION] ?: true,
            doubleSpacePeriod = prefs[Keys.DOUBLE_SPACE_PERIOD] ?: true,
            smartPunctuation = prefs[Keys.SMART_PUNCTUATION] ?: true,
            learnFromTypedWords = prefs[Keys.LEARN_FROM_TYPED_WORDS] ?: true,
            autocorrectSaraiki = prefs[Keys.AUTOCORRECT_SARAIKI] ?: true,
            autocorrectUrdu = prefs[Keys.AUTOCORRECT_URDU] ?: true,
            autocorrectEnglish = prefs[Keys.AUTOCORRECT_ENGLISH] ?: true,
            nextWordPrediction = prefs[Keys.NEXT_WORD_PREDICTION] ?: true,
            glideTypingEnabled = prefs[Keys.GLIDE_TYPING] ?: true,
            showGlideTrail = prefs[Keys.SHOW_GLIDE_TRAIL] ?: true,
            spacebarCursorTrackpad = prefs[Keys.SPACEBAR_TRACKPAD] ?: true,
            gestureDeleteEnabled = prefs[Keys.GESTURE_DELETE] ?: true,
            clipboardEnabled = prefs[Keys.CLIPBOARD_ENABLED] ?: true,
            clipboardRetentionLimit = (prefs[Keys.CLIPBOARD_LIMIT] ?: 25).coerceIn(5, 100),
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            soundVolume = (prefs[Keys.SOUND_VOLUME] ?: 0.55f).coerceIn(0f, 1f),
            soundProfile = SoundProfile.fromId(prefs[Keys.SOUND_PROFILE]),
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            hapticDurationMs = (prefs[Keys.HAPTIC_DURATION] ?: 18).coerceIn(1, 50),
            heightPercent = (prefs[Keys.HEIGHT_PERCENT] ?: 100).coerceIn(60, 130),
            widthPercent = (prefs[Keys.WIDTH_PERCENT] ?: 100).coerceIn(70, 100),
            ergonomicMode = ErgonomicMode.fromId(prefs[Keys.ERGONOMIC_MODE]),
            floatingX = prefs[Keys.FLOATING_X] ?: 24,
            floatingY = prefs[Keys.FLOATING_Y] ?: 160,
            floatingAlpha = (prefs[Keys.FLOATING_ALPHA] ?: 0.96f).coerceIn(0.45f, 1f),
            themePreset = KeyboardThemePreset.fromId(prefs[Keys.THEME_PRESET]),
            customTheme = customTheme
        )
    }

    suspend fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        val current = settingsFlow.value
        val updated = transform(current)
        saveSettings(updated)
    }

    suspend fun saveSettings(updated: KeyboardSettings) {
        val sanitizedEnabled = updated.enabledLanguages.ifEmpty { listOf(KeyboardLanguage.SARAIKI) }
        val sanitizedActive = if (updated.activeLanguage in sanitizedEnabled) updated.activeLanguage else sanitizedEnabled.first()
        context.keyboardDataStore.edit { prefs ->
            prefs[Keys.ENABLED_LANGUAGES] = sanitizedEnabled.joinToString(",") { it.code }
            prefs[Keys.ACTIVE_LANGUAGE] = sanitizedActive.code
            prefs[Keys.MULTILINGUAL_TYPING] = updated.multilingualTyping
            prefs[Keys.SHOW_NUMBER_ROW] = updated.showNumberRow
            prefs[Keys.LONG_PRESS_DELAY] = updated.longPressDelayMs.coerceIn(150, 600)
            prefs[Keys.SHOW_KEYPRESS_PREVIEW] = updated.showKeypressPreview
            prefs[Keys.AUTO_CAPITALIZATION] = updated.autoCapitalization
            prefs[Keys.DOUBLE_SPACE_PERIOD] = updated.doubleSpacePeriod
            prefs[Keys.SMART_PUNCTUATION] = updated.smartPunctuation
            prefs[Keys.LEARN_FROM_TYPED_WORDS] = updated.learnFromTypedWords
            prefs[Keys.AUTOCORRECT_SARAIKI] = updated.autocorrectSaraiki
            prefs[Keys.AUTOCORRECT_URDU] = updated.autocorrectUrdu
            prefs[Keys.AUTOCORRECT_ENGLISH] = updated.autocorrectEnglish
            prefs[Keys.NEXT_WORD_PREDICTION] = updated.nextWordPrediction
            prefs[Keys.GLIDE_TYPING] = updated.glideTypingEnabled
            prefs[Keys.SHOW_GLIDE_TRAIL] = updated.showGlideTrail
            prefs[Keys.SPACEBAR_TRACKPAD] = updated.spacebarCursorTrackpad
            prefs[Keys.GESTURE_DELETE] = updated.gestureDeleteEnabled
            prefs[Keys.CLIPBOARD_ENABLED] = updated.clipboardEnabled
            prefs[Keys.CLIPBOARD_LIMIT] = updated.clipboardRetentionLimit.coerceIn(5, 100)
            prefs[Keys.SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.SOUND_VOLUME] = updated.soundVolume.coerceIn(0f, 1f)
            prefs[Keys.SOUND_PROFILE] = updated.soundProfile.id
            prefs[Keys.HAPTIC_ENABLED] = updated.hapticEnabled
            prefs[Keys.HAPTIC_DURATION] = updated.hapticDurationMs.coerceIn(1, 50)
            prefs[Keys.HEIGHT_PERCENT] = updated.heightPercent.coerceIn(60, 130)
            prefs[Keys.WIDTH_PERCENT] = updated.widthPercent.coerceIn(70, 100)
            prefs[Keys.ERGONOMIC_MODE] = updated.ergonomicMode.id
            prefs[Keys.FLOATING_X] = updated.floatingX
            prefs[Keys.FLOATING_Y] = updated.floatingY
            prefs[Keys.FLOATING_ALPHA] = updated.floatingAlpha.coerceIn(0.45f, 1f)
            prefs[Keys.THEME_PRESET] = updated.themePreset.id

            val c = updated.customTheme
            prefs[Keys.CUSTOM_BG] = c.backgroundColor
            prefs[Keys.CUSTOM_PRIMARY_KEY] = c.primaryKeyColor
            prefs[Keys.CUSTOM_FUNC_KEY] = c.functionKeyColor
            prefs[Keys.CUSTOM_ACCENT] = c.accentColor
            prefs[Keys.CUSTOM_TEXT] = c.primaryTextColor
            prefs[Keys.CUSTOM_HINT] = c.hintTextColor
            prefs[Keys.CUSTOM_WALLPAPER_URI] = c.wallpaperUri.orEmpty()
            prefs[Keys.CUSTOM_WALLPAPER_DIM] = c.wallpaperDim
            prefs[Keys.CUSTOM_WALLPAPER_BLUR] = c.wallpaperBlur
            prefs[Keys.CUSTOM_KEY_OPACITY] = c.keyOpacity
            prefs[Keys.CUSTOM_KEY_BORDERS] = c.showKeyBorders
            prefs[Keys.CUSTOM_CORNER_RADIUS] = c.cornerRadiusDp
            prefs[Keys.CUSTOM_FONT_SIZE] = c.labelFontSizeSp
            prefs[Keys.CUSTOM_SECONDARY_HINTS] = c.showSecondaryHints
        }
    }

    suspend fun setActiveLanguage(language: KeyboardLanguage) {
        updateSettings { it.copy(activeLanguage = language) }
    }

    suspend fun cycleNextLanguage(): KeyboardLanguage {
        val current = settingsFlow.value
        val list = current.enabledLanguages.ifEmpty { listOf(KeyboardLanguage.SARAIKI) }
        val idx = list.indexOf(current.activeLanguage)
        val next = list[(idx + 1) % list.size]
        setActiveLanguage(next)
        return next
    }

    fun exportSettingsJson(): String {
        val s = settingsFlow.value
        return JSONObject().apply {
            put("enabledLanguages", JSONArray(s.enabledLanguages.map { it.code }))
            put("activeLanguage", s.activeLanguage.code)
            put("multilingualTyping", s.multilingualTyping)
            put("showNumberRow", s.showNumberRow)
            put("longPressDelayMs", s.longPressDelayMs)
            put("showKeypressPreview", s.showKeypressPreview)
            put("autoCapitalization", s.autoCapitalization)
            put("doubleSpacePeriod", s.doubleSpacePeriod)
            put("smartPunctuation", s.smartPunctuation)
            put("learnFromTypedWords", s.learnFromTypedWords)
            put("autocorrectSaraiki", s.autocorrectSaraiki)
            put("autocorrectUrdu", s.autocorrectUrdu)
            put("autocorrectEnglish", s.autocorrectEnglish)
            put("nextWordPrediction", s.nextWordPrediction)
            put("glideTypingEnabled", s.glideTypingEnabled)
            put("showGlideTrail", s.showGlideTrail)
            put("spacebarCursorTrackpad", s.spacebarCursorTrackpad)
            put("gestureDeleteEnabled", s.gestureDeleteEnabled)
            put("clipboardEnabled", s.clipboardEnabled)
            put("clipboardRetentionLimit", s.clipboardRetentionLimit)
            put("soundEnabled", s.soundEnabled)
            put("soundVolume", s.soundVolume.toDouble())
            put("soundProfile", s.soundProfile.id)
            put("hapticEnabled", s.hapticEnabled)
            put("hapticDurationMs", s.hapticDurationMs)
            put("heightPercent", s.heightPercent)
            put("widthPercent", s.widthPercent)
            put("ergonomicMode", s.ergonomicMode.id)
            put("themePreset", s.themePreset.id)
            put("customTheme", JSONObject().apply {
                put("backgroundColor", s.customTheme.backgroundColor)
                put("primaryKeyColor", s.customTheme.primaryKeyColor)
                put("functionKeyColor", s.customTheme.functionKeyColor)
                put("accentColor", s.customTheme.accentColor)
                put("primaryTextColor", s.customTheme.primaryTextColor)
                put("hintTextColor", s.customTheme.hintTextColor)
                put("wallpaperDim", s.customTheme.wallpaperDim.toDouble())
                put("wallpaperBlur", s.customTheme.wallpaperBlur.toDouble())
                put("keyOpacity", s.customTheme.keyOpacity.toDouble())
                put("showKeyBorders", s.customTheme.showKeyBorders)
                put("cornerRadiusDp", s.customTheme.cornerRadiusDp.toDouble())
                put("labelFontSizeSp", s.customTheme.labelFontSizeSp.toDouble())
                put("showSecondaryHints", s.customTheme.showSecondaryHints)
            })
        }.toString(2)
    }

    suspend fun importSettingsJson(jsonString: String) {
        val obj = JSONObject(jsonString)
        val langsArr = obj.optJSONArray("enabledLanguages")
        val enabled = mutableListOf<KeyboardLanguage>()
        if (langsArr != null) {
            for (i in 0 until langsArr.length()) {
                KeyboardLanguage.entries.firstOrNull { it.code == langsArr.optString(i) }?.let { enabled.add(it) }
            }
        }
        val customObj = obj.optJSONObject("customTheme")
        val current = settingsFlow.value
        val custom = if (customObj != null) {
            current.customTheme.copy(
                backgroundColor = customObj.optInt("backgroundColor", current.customTheme.backgroundColor),
                primaryKeyColor = customObj.optInt("primaryKeyColor", current.customTheme.primaryKeyColor),
                functionKeyColor = customObj.optInt("functionKeyColor", current.customTheme.functionKeyColor),
                accentColor = customObj.optInt("accentColor", current.customTheme.accentColor),
                primaryTextColor = customObj.optInt("primaryTextColor", current.customTheme.primaryTextColor),
                hintTextColor = customObj.optInt("hintTextColor", current.customTheme.hintTextColor),
                wallpaperDim = customObj.optDouble("wallpaperDim", current.customTheme.wallpaperDim.toDouble()).toFloat(),
                wallpaperBlur = customObj.optDouble("wallpaperBlur", current.customTheme.wallpaperBlur.toDouble()).toFloat(),
                keyOpacity = customObj.optDouble("keyOpacity", current.customTheme.keyOpacity.toDouble()).toFloat(),
                showKeyBorders = customObj.optBoolean("showKeyBorders", current.customTheme.showKeyBorders),
                cornerRadiusDp = customObj.optDouble("cornerRadiusDp", current.customTheme.cornerRadiusDp.toDouble()).toFloat(),
                labelFontSizeSp = customObj.optDouble("labelFontSizeSp", current.customTheme.labelFontSizeSp.toDouble()).toFloat(),
                showSecondaryHints = customObj.optBoolean("showSecondaryHints", current.customTheme.showSecondaryHints)
            )
        } else current.customTheme

        saveSettings(
            current.copy(
                enabledLanguages = enabled.ifEmpty { current.enabledLanguages },
                activeLanguage = KeyboardLanguage.fromCode(obj.optString("activeLanguage", current.activeLanguage.code)),
                multilingualTyping = obj.optBoolean("multilingualTyping", current.multilingualTyping),
                showNumberRow = obj.optBoolean("showNumberRow", current.showNumberRow),
                longPressDelayMs = obj.optInt("longPressDelayMs", current.longPressDelayMs),
                showKeypressPreview = obj.optBoolean("showKeypressPreview", current.showKeypressPreview),
                autoCapitalization = obj.optBoolean("autoCapitalization", current.autoCapitalization),
                doubleSpacePeriod = obj.optBoolean("doubleSpacePeriod", current.doubleSpacePeriod),
                smartPunctuation = obj.optBoolean("smartPunctuation", current.smartPunctuation),
                learnFromTypedWords = obj.optBoolean("learnFromTypedWords", current.learnFromTypedWords),
                autocorrectSaraiki = obj.optBoolean("autocorrectSaraiki", current.autocorrectSaraiki),
                autocorrectUrdu = obj.optBoolean("autocorrectUrdu", current.autocorrectUrdu),
                autocorrectEnglish = obj.optBoolean("autocorrectEnglish", current.autocorrectEnglish),
                nextWordPrediction = obj.optBoolean("nextWordPrediction", current.nextWordPrediction),
                glideTypingEnabled = obj.optBoolean("glideTypingEnabled", current.glideTypingEnabled),
                showGlideTrail = obj.optBoolean("showGlideTrail", current.showGlideTrail),
                spacebarCursorTrackpad = obj.optBoolean("spacebarCursorTrackpad", current.spacebarCursorTrackpad),
                gestureDeleteEnabled = obj.optBoolean("gestureDeleteEnabled", current.gestureDeleteEnabled),
                clipboardEnabled = obj.optBoolean("clipboardEnabled", current.clipboardEnabled),
                clipboardRetentionLimit = obj.optInt("clipboardRetentionLimit", current.clipboardRetentionLimit),
                soundEnabled = obj.optBoolean("soundEnabled", current.soundEnabled),
                soundVolume = obj.optDouble("soundVolume", current.soundVolume.toDouble()).toFloat(),
                soundProfile = SoundProfile.fromId(obj.optString("soundProfile", current.soundProfile.id)),
                hapticEnabled = obj.optBoolean("hapticEnabled", current.hapticEnabled),
                hapticDurationMs = obj.optInt("hapticDurationMs", current.hapticDurationMs),
                heightPercent = obj.optInt("heightPercent", current.heightPercent),
                widthPercent = obj.optInt("widthPercent", current.widthPercent),
                ergonomicMode = ErgonomicMode.fromId(obj.optString("ergonomicMode", current.ergonomicMode.id)),
                themePreset = KeyboardThemePreset.fromId(obj.optString("themePreset", current.themePreset.id)),
                customTheme = custom
            )
        )
    }
}
