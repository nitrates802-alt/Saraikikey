package com.example.core.preferences

import android.graphics.Color as AndroidColor

enum class KeyboardLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean
) {
    SARAIKI("srk", "سرائیکی", "Saraiki", isRtl = true),
    URDU("urd", "اردو", "Urdu", isRtl = true),
    ENGLISH("eng", "English", "English", isRtl = false);

    companion object {
        fun fromCode(code: String?): KeyboardLanguage =
            entries.firstOrNull { it.code == code } ?: SARAIKI
    }
}

enum class KeyboardThemePreset(val id: String, val displayName: String, val subtitle: String) {
    SARAIKI_PRO_DARK("saraiki_pro_dark", "Saraiki Pro Dark", "Deep slate #1E222B with crisp cyan accents"),
    AMOLED_BLACK("amoled_black", "AMOLED Pure Black", "True #000000 canvas for OLED battery savings"),
    CLEAN_DAYLIGHT("clean_daylight", "Clean Daylight", "Crisp #ECEFF4 / #FFFFFF Material light layout"),
    MATERIAL_YOU("material_you", "Material You Dynamic", "Adapts to Android 12+ wallpaper colors"),
    MULTAN_HERITAGE("multan_heritage", "Multan Heritage Teal & Sand", "Glazed Multani tile teal with sandstone accents"),
    CUSTOM("custom", "Custom Studio Theme", "Personalized colors, wallpaper, radius & borders");

    companion object {
        fun fromId(id: String?): KeyboardThemePreset =
            entries.firstOrNull { it.id == id } ?: SARAIKI_PRO_DARK
    }
}

enum class SoundProfile(val id: String, val displayName: String) {
    SOFT_CLICK("soft_click", "Gboard Soft Click"),
    MECHANICAL_TACTILE("mechanical", "Mechanical Tactile"),
    TYPEWRITER("typewriter", "Typewriter"),
    WATERDROP("waterdrop", "Waterdrop");

    companion object {
        fun fromId(id: String?): SoundProfile =
            entries.firstOrNull { it.id == id } ?: SOFT_CLICK
    }
}

enum class ErgonomicMode(val id: String, val displayName: String) {
    NORMAL("normal", "Standard Full Width"),
    ONE_HANDED_LEFT("one_handed_left", "One-Handed (Left)"),
    ONE_HANDED_RIGHT("one_handed_right", "One-Handed (Right)"),
    FLOATING("floating", "Floating / Undocked"),
    SPLIT_TABLET("split_tablet", "Split Thumb (Tablet)");

    companion object {
        fun fromId(id: String?): ErgonomicMode =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

data class CustomThemeConfig(
    val backgroundColor: Int = AndroidColor.parseColor("#1E222B"),
    val primaryKeyColor: Int = AndroidColor.parseColor("#38404F"),
    val functionKeyColor: Int = AndroidColor.parseColor("#2A303C"),
    val accentColor: Int = AndroidColor.parseColor("#38BDF8"),
    val primaryTextColor: Int = AndroidColor.WHITE,
    val hintTextColor: Int = AndroidColor.parseColor("#9BA3B4"),
    val wallpaperUri: String? = null,
    val wallpaperDim: Float = 0.45f,
    val wallpaperBlur: Float = 0f,
    val keyOpacity: Float = 0.92f,
    val showKeyBorders: Boolean = false,
    val cornerRadiusDp: Float = 8f,
    val labelFontSizeSp: Float = 20f,
    val showSecondaryHints: Boolean = true
)

data class KeyboardSettings(
    val enabledLanguages: List<KeyboardLanguage> = listOf(
        KeyboardLanguage.SARAIKI,
        KeyboardLanguage.URDU,
        KeyboardLanguage.ENGLISH
    ),
    val activeLanguage: KeyboardLanguage = KeyboardLanguage.SARAIKI,
    val multilingualTyping: Boolean = true,
    val showNumberRow: Boolean = false,
    val longPressDelayMs: Int = 280,
    val showKeypressPreview: Boolean = true,
    val autoCapitalization: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val smartPunctuation: Boolean = true,
    val learnFromTypedWords: Boolean = true,
    val autocorrectSaraiki: Boolean = true,
    val autocorrectUrdu: Boolean = true,
    val autocorrectEnglish: Boolean = true,
    val nextWordPrediction: Boolean = true,
    val glideTypingEnabled: Boolean = true,
    val showGlideTrail: Boolean = true,
    val spacebarCursorTrackpad: Boolean = true,
    val gestureDeleteEnabled: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val clipboardRetentionLimit: Int = 25,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.55f,
    val soundProfile: SoundProfile = SoundProfile.SOFT_CLICK,
    val hapticEnabled: Boolean = true,
    val hapticDurationMs: Int = 18,
    val heightPercent: Int = 100,
    val widthPercent: Int = 100,
    val ergonomicMode: ErgonomicMode = ErgonomicMode.NORMAL,
    val floatingX: Int = 24,
    val floatingY: Int = 160,
    val floatingAlpha: Float = 0.96f,
    val themePreset: KeyboardThemePreset = KeyboardThemePreset.SARAIKI_PRO_DARK,
    val customTheme: CustomThemeConfig = CustomThemeConfig()
)
