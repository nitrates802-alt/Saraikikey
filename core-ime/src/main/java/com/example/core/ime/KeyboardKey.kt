package com.example.core.ime

import android.graphics.RectF

/**
 * Represents a single key on the custom Saraiki Pro Keyboard, encapsulating its character,
 * numeric key code, optional long-press alternatives, and visual layout weight/dimensions.
 */
data class KeyboardKey(
    val character: String,
    val keyCode: Int = resolveKeyCode(character, KeyAction.CommitChar(character)),
    val longPressAlternatives: List<String> = emptyList(),
    val longPressCenterIndex: Int = 0,
    val hintLabel: String? = null,
    val widthWeight: Float = 1f,
    val heightWeight: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val isFunctional: Boolean = false,
    val isAccent: Boolean = false,
    val action: KeyAction = KeyAction.CommitChar(character)
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    fun toRectF(): RectF = RectF(x, y, right, bottom)

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px < right && py >= y && py < bottom

    fun withBounds(x: Float, y: Float, width: Float, height: Float): KeyboardKey =
        copy(x = x, y = y, width = width, height = height)

    companion object {
        const val KEYCODE_SHIFT = -1
        const val KEYCODE_SYMBOLS = -2
        const val KEYCODE_DELETE = -5
        const val KEYCODE_MORE_SYMBOLS = -6
        const val KEYCODE_EMOJI = -7
        const val KEYCODE_GLOBE = -8
        const val KEYCODE_SPACE = 32
        const val KEYCODE_ENTER = 10
        const val KEYCODE_SPACER = -99

        fun create(
            character: String,
            action: KeyAction = KeyAction.CommitChar(character),
            longPressAlternatives: List<String> = emptyList(),
            longPressCenterIndex: Int = 0,
            hintLabel: String? = null,
            widthWeight: Float = 1f,
            heightWeight: Float = 1f,
            isFunctional: Boolean = false,
            isAccent: Boolean = false
        ): KeyboardKey = KeyboardKey(
            character = character,
            keyCode = resolveKeyCode(character, action),
            longPressAlternatives = longPressAlternatives,
            longPressCenterIndex = longPressCenterIndex,
            hintLabel = hintLabel,
            widthWeight = widthWeight,
            heightWeight = heightWeight,
            isFunctional = isFunctional,
            isAccent = isAccent,
            action = action
        )

        fun resolveKeyCode(character: String, action: KeyAction): Int = when (action) {
            is KeyAction.CommitChar -> if (action.char.isNotEmpty()) action.char.codePointAt(0) else 0
            KeyAction.Shift -> KEYCODE_SHIFT
            KeyAction.Backspace -> KEYCODE_DELETE
            KeyAction.ToggleSymbols -> KEYCODE_SYMBOLS
            KeyAction.ToggleMoreSymbols -> KEYCODE_MORE_SYMBOLS
            KeyAction.Comma -> if (character.isNotEmpty()) character.codePointAt(0) else ','.code
            KeyAction.EmojiDrawer -> KEYCODE_EMOJI
            KeyAction.GlobeSwitch -> KEYCODE_GLOBE
            KeyAction.Space -> KEYCODE_SPACE
            KeyAction.Period -> if (character.isNotEmpty()) character.codePointAt(0) else '.'.code
            KeyAction.Enter -> KEYCODE_ENTER
            KeyAction.Spacer -> KEYCODE_SPACER
        }
    }
}
