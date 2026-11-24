package com.example.core.ime

/**
 * Typed IME actions triggered by tapping or committing a [KeyboardKey].
 */
sealed interface KeyAction {
    data class CommitChar(val char: String) : KeyAction
    data object Shift : KeyAction
    data object Backspace : KeyAction
    data object ToggleSymbols : KeyAction
    data object ToggleMoreSymbols : KeyAction
    data object Comma : KeyAction
    data object EmojiDrawer : KeyAction
    data object GlobeSwitch : KeyAction
    data object Space : KeyAction
    data object Period : KeyAction
    data object Enter : KeyAction
    data object Spacer : KeyAction
}
