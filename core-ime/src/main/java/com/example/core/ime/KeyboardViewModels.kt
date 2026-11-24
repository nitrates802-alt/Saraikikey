package com.example.core.ime

import android.graphics.Color

data class ResolvedKeyboardTheme(
    val backgroundColor: Int,
    val stripBackgroundColor: Int,
    val primaryKeyColor: Int,
    val primaryKeyPressedColor: Int,
    val functionKeyColor: Int,
    val functionKeyPressedColor: Int,
    val accentColor: Int,
    val accentTextColor: Int,
    val primaryTextColor: Int,
    val hintTextColor: Int,
    val borderColor: Int,
    val popupBackgroundColor: Int,
    val popupHighlightColor: Int,
    val wallpaperUri: String?,
    val wallpaperDim: Float,
    val wallpaperBlur: Float,
    val keyOpacity: Float,
    val showKeyBorders: Boolean,
    val cornerRadiusDp: Float,
    val labelFontSizeSp: Float,
    val showSecondaryHints: Boolean
) {
    companion object {
        fun defaultDark(): ResolvedKeyboardTheme = ResolvedKeyboardTheme(
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
            cornerRadiusDp = 8f,
            labelFontSizeSp = 20f,
            showSecondaryHints = true
        )
    }
}

data class KeyboardViewOptions(
    val heightPercent: Int = 100,
    val showKeypressPreview: Boolean = true,
    val glideTypingEnabled: Boolean = true,
    val showGlideTrail: Boolean = true,
    val spacebarCursorTrackpad: Boolean = true,
    val gestureDeleteEnabled: Boolean = true,
    val longPressDelayMs: Int = 280
)

data class KeyCenter(
    val label: String,
    val x: Float,
    val y: Float
)

data class GlidePoint(
    val x: Float,
    val y: Float,
    val t: Long
)

interface KeyboardInteractionListener {
    fun onKeyPressed(key: KeyboardKey) {}
    fun onKeyAction(action: KeyAction)
    fun onPopupItemCommitted(text: String)
    fun onGlideCompleted(points: List<GlidePoint>, keyCenters: List<KeyCenter>)
    fun onSpacebarCursorDelta(steps: Int)
    fun onSpacebarLongPress()
    fun onGestureDeleteWord()
    fun onBackspaceRepeatStep(deleteWholeWord: Boolean)
    fun onPopupFocusTick()
}
