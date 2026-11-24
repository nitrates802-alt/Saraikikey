package com.example.core.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import com.example.core.preferences.KeyboardSettings
import com.example.core.preferences.KeyboardThemePreset

fun KeyboardSettings.toViewOptions(): KeyboardViewOptions = KeyboardViewOptions(
    heightPercent = heightPercent,
    showKeypressPreview = showKeypressPreview,
    glideTypingEnabled = glideTypingEnabled,
    showGlideTrail = showGlideTrail,
    spacebarCursorTrackpad = spacebarCursorTrackpad,
    gestureDeleteEnabled = gestureDeleteEnabled,
    longPressDelayMs = longPressDelayMs
)

fun CanvasKeyboardView.updateState(
    newRows: List<List<KeyboardKey>>,
    newSettings: KeyboardSettings,
    newTheme: ResolvedKeyboardTheme
) = updateState(newRows, newSettings.toViewOptions(), newTheme)

object KeyboardThemeTokens {

    fun resolve(context: Context, settings: KeyboardSettings): ResolvedKeyboardTheme {
        val c = settings.customTheme
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        return when (settings.themePreset) {
            KeyboardThemePreset.SARAIKI_PRO_DARK -> ResolvedKeyboardTheme(
                backgroundColor = Color.parseColor("#1E222B"),
                stripBackgroundColor = Color.parseColor("#171A21"),
                primaryKeyColor = Color.parseColor("#38404F"),
                primaryKeyPressedColor = Color.parseColor("#4B566B"),
                functionKeyColor = Color.parseColor("#2A303C"),
                functionKeyPressedColor = Color.parseColor("#3B4354"),
                accentColor = Color.parseColor("#38BDF8"),
                accentTextColor = Color.parseColor("#0B141D"),
                primaryTextColor = Color.WHITE,
                hintTextColor = Color.parseColor("#9BA3B4"),
                borderColor = Color.parseColor("#4A5568"),
                popupBackgroundColor = Color.parseColor("#2B3240"),
                popupHighlightColor = Color.parseColor("#38BDF8"),
                wallpaperUri = null,
                wallpaperDim = 0f,
                wallpaperBlur = 0f,
                keyOpacity = 1f,
                showKeyBorders = false,
                cornerRadiusDp = c.cornerRadiusDp,
                labelFontSizeSp = c.labelFontSizeSp,
                showSecondaryHints = c.showSecondaryHints
            )

            KeyboardThemePreset.AMOLED_BLACK -> ResolvedKeyboardTheme(
                backgroundColor = Color.BLACK,
                stripBackgroundColor = Color.BLACK,
                primaryKeyColor = Color.parseColor("#16181D"),
                primaryKeyPressedColor = Color.parseColor("#272B34"),
                functionKeyColor = Color.parseColor("#0E1014"),
                functionKeyPressedColor = Color.parseColor("#1F232B"),
                accentColor = Color.parseColor("#38BDF8"),
                accentTextColor = Color.BLACK,
                primaryTextColor = Color.WHITE,
                hintTextColor = Color.parseColor("#8A93A5"),
                borderColor = Color.parseColor("#2A2F3A"),
                popupBackgroundColor = Color.parseColor("#1E222A"),
                popupHighlightColor = Color.parseColor("#38BDF8"),
                wallpaperUri = null,
                wallpaperDim = 0f,
                wallpaperBlur = 0f,
                keyOpacity = 1f,
                showKeyBorders = true,
                cornerRadiusDp = c.cornerRadiusDp,
                labelFontSizeSp = c.labelFontSizeSp,
                showSecondaryHints = c.showSecondaryHints
            )

            KeyboardThemePreset.CLEAN_DAYLIGHT -> ResolvedKeyboardTheme(
                backgroundColor = Color.parseColor("#ECEFF4"),
                stripBackgroundColor = Color.parseColor("#E2E6EE"),
                primaryKeyColor = Color.WHITE,
                primaryKeyPressedColor = Color.parseColor("#D8DEE9"),
                functionKeyColor = Color.parseColor("#D8DEE9"),
                functionKeyPressedColor = Color.parseColor("#C5CEDE"),
                accentColor = Color.parseColor("#0284C7"),
                accentTextColor = Color.WHITE,
                primaryTextColor = Color.parseColor("#1E222B"),
                hintTextColor = Color.parseColor("#64748B"),
                borderColor = Color.parseColor("#CBD5E1"),
                popupBackgroundColor = Color.WHITE,
                popupHighlightColor = Color.parseColor("#0284C7"),
                wallpaperUri = null,
                wallpaperDim = 0f,
                wallpaperBlur = 0f,
                keyOpacity = 1f,
                showKeyBorders = false,
                cornerRadiusDp = c.cornerRadiusDp,
                labelFontSizeSp = c.labelFontSizeSp,
                showSecondaryHints = c.showSecondaryHints
            )

            KeyboardThemePreset.MATERIAL_YOU -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val bg = context.getColor(if (isNight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_100)
                    val strip = context.getColor(if (isNight) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_200)
                    val key = context.getColor(if (isNight) android.R.color.system_neutral1_700 else android.R.color.system_neutral1_0)
                    val func = context.getColor(if (isNight) android.R.color.system_neutral2_800 else android.R.color.system_neutral2_200)
                    val accent = context.getColor(if (isNight) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
                    val accentText = context.getColor(if (isNight) android.R.color.system_accent1_900 else android.R.color.system_neutral1_0)
                    val text = context.getColor(if (isNight) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900)
                    val hint = context.getColor(if (isNight) android.R.color.system_neutral2_300 else android.R.color.system_neutral2_600)
                    ResolvedKeyboardTheme(
                        backgroundColor = bg,
                        stripBackgroundColor = strip,
                        primaryKeyColor = key,
                        primaryKeyPressedColor = func,
                        functionKeyColor = func,
                        functionKeyPressedColor = accent,
                        accentColor = accent,
                        accentTextColor = accentText,
                        primaryTextColor = text,
                        hintTextColor = hint,
                        borderColor = hint,
                        popupBackgroundColor = func,
                        popupHighlightColor = accent,
                        wallpaperUri = null,
                        wallpaperDim = 0f,
                        wallpaperBlur = 0f,
                        keyOpacity = 1f,
                        showKeyBorders = false,
                        cornerRadiusDp = c.cornerRadiusDp,
                        labelFontSizeSp = c.labelFontSizeSp,
                        showSecondaryHints = c.showSecondaryHints
                    )
                } else {
                    resolve(context, settings.copy(themePreset = KeyboardThemePreset.SARAIKI_PRO_DARK))
                }
            }

            KeyboardThemePreset.MULTAN_HERITAGE -> ResolvedKeyboardTheme(
                backgroundColor = Color.parseColor("#0D262B"),
                stripBackgroundColor = Color.parseColor("#091D21"),
                primaryKeyColor = Color.parseColor("#173D45"),
                primaryKeyPressedColor = Color.parseColor("#235560"),
                functionKeyColor = Color.parseColor("#112E34"),
                functionKeyPressedColor = Color.parseColor("#1E4952"),
                accentColor = Color.parseColor("#E6C79C"),
                accentTextColor = Color.parseColor("#142326"),
                primaryTextColor = Color.parseColor("#F8F3EA"),
                hintTextColor = Color.parseColor("#9CC2C9"),
                borderColor = Color.parseColor("#2B626E"),
                popupBackgroundColor = Color.parseColor("#18444D"),
                popupHighlightColor = Color.parseColor("#E6C79C"),
                wallpaperUri = null,
                wallpaperDim = 0f,
                wallpaperBlur = 0f,
                keyOpacity = 1f,
                showKeyBorders = true,
                cornerRadiusDp = c.cornerRadiusDp,
                labelFontSizeSp = c.labelFontSizeSp,
                showSecondaryHints = c.showSecondaryHints
            )

            KeyboardThemePreset.CUSTOM -> {
                val alphaByte = (c.keyOpacity.coerceIn(0.25f, 1f) * 255).toInt()
                val applyAlpha = { color: Int ->
                    Color.argb(alphaByte, Color.red(color), Color.green(color), Color.blue(color))
                }
                ResolvedKeyboardTheme(
                    backgroundColor = c.backgroundColor,
                    stripBackgroundColor = darkenColor(c.backgroundColor, 0.85f),
                    primaryKeyColor = applyAlpha(c.primaryKeyColor),
                    primaryKeyPressedColor = lightenColor(c.primaryKeyColor, 1.2f),
                    functionKeyColor = applyAlpha(c.functionKeyColor),
                    functionKeyPressedColor = lightenColor(c.functionKeyColor, 1.2f),
                    accentColor = c.accentColor,
                    accentTextColor = if (isColorDark(c.accentColor)) Color.WHITE else Color.BLACK,
                    primaryTextColor = c.primaryTextColor,
                    hintTextColor = c.hintTextColor,
                    borderColor = applyAlpha(c.hintTextColor),
                    popupBackgroundColor = c.functionKeyColor,
                    popupHighlightColor = c.accentColor,
                    wallpaperUri = c.wallpaperUri,
                    wallpaperDim = c.wallpaperDim,
                    wallpaperBlur = c.wallpaperBlur,
                    keyOpacity = c.keyOpacity,
                    showKeyBorders = c.showKeyBorders,
                    cornerRadiusDp = c.cornerRadiusDp,
                    labelFontSizeSp = c.labelFontSizeSp,
                    showSecondaryHints = c.showSecondaryHints
                )
            }
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return luminance < 0.5
    }

    private fun darkenColor(color: Int, factor: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun lightenColor(color: Int, factor: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}
