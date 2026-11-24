package com.example.core.ime

import android.view.inputmethod.EditorInfo

/**
 * Supported IME languages cycled by the dedicated globe key in [SaraikiKeyboardService].
 */
enum class ImeLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val isRtl: Boolean
) {
    SARAIKI("srk", "سرائیکی", "Saraiki", isRtl = true),
    URDU("urd", "اردو", "Urdu", isRtl = true),
    ENGLISH("eng", "English", "English", isRtl = false);

    fun next(): ImeLanguage {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    companion object {
        fun fromCode(code: String?): ImeLanguage =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) } ?: SARAIKI
    }
}

enum class CoreKeyboardPage {
    LETTERS,
    SYMBOLS
}

/**
 * Provides default Saraiki, Urdu, and English keyboard key rows with a dedicated
 * globe key ([KeyAction.GlobeSwitch]) for [SaraikiKeyboardService] in `:core-ime`.
 */
object CoreKeyboardLayouts {

    fun buildRows(
        language: ImeLanguage,
        page: CoreKeyboardPage = CoreKeyboardPage.LETTERS,
        shifted: Boolean = false,
        imeOptions: Int = EditorInfo.IME_ACTION_NONE
    ): List<List<KeyboardKey>> {
        val letterRows = when (page) {
            CoreKeyboardPage.SYMBOLS -> buildSymbolsRows(language)
            CoreKeyboardPage.LETTERS -> when (language) {
                ImeLanguage.SARAIKI -> buildSaraikiRows(shifted)
                ImeLanguage.URDU -> buildUrduRows(shifted)
                ImeLanguage.ENGLISH -> buildEnglishRows(shifted)
            }
        }
        return letterRows + listOf(buildBottomRow(language, page, imeOptions))
    }

    private fun buildSaraikiRows(shifted: Boolean): List<List<KeyboardKey>> {
        if (shifted) {
            val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
                KeyboardKey.create(character = it, action = KeyAction.CommitChar(it))
            }
            val r2 = listOf("ٻ", "ڄ", "ڳ", "ڱ", "ڋ", "ݨ", "ݮ", "آ", "ؤ", "ئ").map {
                KeyboardKey.create(character = it, action = KeyAction.CommitChar(it))
            }
            val r3 = listOf("َ", "ِ", "ُ", "ّ", "ْ", "ً", "ٰ", "ۂ", "ژ", "؟").map {
                KeyboardKey.create(character = it, action = KeyAction.CommitChar(it))
            }
            val r4 = buildList {
                add(KeyboardKey.create("⇧", widthWeight = 1.4f, isFunctional = true, isAccent = true, action = KeyAction.Shift))
                listOf("«", "»", "؎", "؏", "٪", "،", "؛", "۔").forEach {
                    add(KeyboardKey.create(character = it, action = KeyAction.CommitChar(it)))
                }
                add(KeyboardKey.create("⌫", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Backspace))
            }
            return listOf(r1, r2, r3, r4)
        }

        val r1 = listOf("ق", "و", "ع", "ر", "ت", "ے", "ی", "ء", "ہ", "پ").mapIndexed { index, ch ->
            val digit = ((index + 1) % 10).toString()
            KeyboardKey.create(
                character = ch,
                hintLabel = digit,
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = listOf(digit)
            )
        }
        val r2 = listOf("ا", "س", "د", "ف", "گ", "ح", "ج", "ک", "ل", "ط").map { ch ->
            val alts = when (ch) {
                "ا" -> listOf("آ", "أ", "إ")
                "د" -> listOf("ڋ", "ڈ", "ذ")
                "گ" -> listOf("ڱ", "ڳ", "غ")
                "ج" -> listOf("ڄ")
                else -> emptyList()
            }
            KeyboardKey.create(
                character = ch,
                hintLabel = alts.firstOrNull(),
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = alts
            )
        }
        val r3 = listOf("ز", "ش", "چ", "خ", "غ", "ب", "ن", "م", "ڑ", "ڈ").map { ch ->
            val alts = when (ch) {
                "چ" -> listOf("ݮ")
                "ب" -> listOf("ٻ")
                "ن" -> listOf("ݨ", "ں")
                else -> emptyList()
            }
            KeyboardKey.create(
                character = ch,
                hintLabel = alts.firstOrNull(),
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = alts
            )
        }
        val r4 = buildList {
            add(KeyboardKey.create("⇧", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Shift))
            listOf("ٹ", "ص", "ض", "ذ", "ث", "ظ", "ں", "ھ").forEach { ch ->
                add(KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3, r4)
    }

    private fun buildUrduRows(shifted: Boolean): List<List<KeyboardKey>> {
        if (shifted) {
            val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
                KeyboardKey.create(character = it, action = KeyAction.CommitChar(it))
            }
            val r2 = listOf("آ", "أ", "إ", "ؤ", "ئ", "ۂ", "ۃ", "ژ", "ض", "ظ").map {
                KeyboardKey.create(character = it, action = KeyAction.CommitChar(it))
            }
            val r3 = listOf("َ", "ِ", "ُ", "ّ", "ْ", "ً", "ٰ", "؎", "؏", "؟").map {
                KeyboardKey.create(character = it, action = KeyAction.CommitChar(it))
            }
            val r4 = buildList {
                add(KeyboardKey.create("⇧", widthWeight = 1.4f, isFunctional = true, isAccent = true, action = KeyAction.Shift))
                listOf("«", "»", "÷", "×", "٪", "،", "؛", "۔").forEach {
                    add(KeyboardKey.create(character = it, action = KeyAction.CommitChar(it)))
                }
                add(KeyboardKey.create("⌫", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Backspace))
            }
            return listOf(r1, r2, r3, r4)
        }

        val r1 = listOf("ق", "و", "ع", "ر", "ت", "ے", "ی", "ء", "ہ", "پ").mapIndexed { index, ch ->
            val digit = ((index + 1) % 10).toString()
            KeyboardKey.create(
                character = ch,
                hintLabel = digit,
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = listOf(digit)
            )
        }
        val r2 = listOf("ا", "س", "د", "ف", "گ", "ح", "ج", "ک", "ل", "ط").map { ch ->
            val alts = when (ch) {
                "ا" -> listOf("آ", "أ", "إ")
                "د" -> listOf("ڈ", "ذ")
                "ج" -> listOf("چ")
                "گ" -> listOf("غ")
                else -> emptyList()
            }
            KeyboardKey.create(
                character = ch,
                hintLabel = alts.firstOrNull(),
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = alts
            )
        }
        val r3 = listOf("ز", "ش", "چ", "خ", "غ", "ب", "ن", "م", "ڑ", "ڈ").map { ch ->
            KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch))
        }
        val r4 = buildList {
            add(KeyboardKey.create("⇧", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Shift))
            listOf("ٹ", "ص", "ض", "ذ", "ث", "ظ", "ں", "ھ").forEach { ch ->
                add(KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3, r4)
    }

    private fun buildEnglishRows(shifted: Boolean): List<List<KeyboardKey>> {
        val r1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p").mapIndexed { idx, raw ->
            val ch = if (shifted) raw.uppercase() else raw
            val digit = ((idx + 1) % 10).toString()
            KeyboardKey.create(
                character = ch,
                hintLabel = digit,
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = listOf(digit)
            )
        }
        val r2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l").map { raw ->
            val ch = if (shifted) raw.uppercase() else raw
            KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch))
        }
        val r3 = buildList {
            add(
                KeyboardKey.create(
                    character = if (shifted) "⬆" else "⇧",
                    widthWeight = 1.5f,
                    isFunctional = true,
                    isAccent = shifted,
                    action = KeyAction.Shift
                )
            )
            listOf("z", "x", "c", "v", "b", "n", "m").forEach { raw ->
                val ch = if (shifted) raw.uppercase() else raw
                add(KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.5f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3)
    }

    private fun buildSymbolsRows(language: ImeLanguage): List<List<KeyboardKey>> {
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            KeyboardKey.create(it, action = KeyAction.CommitChar(it))
        }
        val r2 = listOf("@", "#", "₨", "_", "&", "-", "+", "(", ")", "/").map {
            KeyboardKey.create(it, action = KeyAction.CommitChar(it))
        }
        val qMark = if (language.isRtl) "؟" else "?"
        val r3 = buildList {
            add(KeyboardKey.create("ABC", widthWeight = 1.5f, isFunctional = true, action = KeyAction.ToggleSymbols))
            listOf("*", "\"", "'", ":", ";", "!", qMark).forEach {
                add(KeyboardKey.create(it, action = KeyAction.CommitChar(it)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.5f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3)
    }

    private fun buildBottomRow(
        language: ImeLanguage,
        page: CoreKeyboardPage,
        imeOptions: Int
    ): List<KeyboardKey> {
        val toggleLabel = if (page == CoreKeyboardPage.LETTERS) "?123" else "ABC"
        val commaChar = if (language.isRtl) "،" else ","
        val periodChar = if (language.isRtl) "۔" else "."
        val actionType = imeOptions and EditorInfo.IME_MASK_ACTION
        val enterLabel = when (actionType) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_GO -> "→"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "↵"
        }

        return listOf(
            KeyboardKey.create(toggleLabel, widthWeight = 1.35f, isFunctional = true, action = KeyAction.ToggleSymbols),
            KeyboardKey.create(commaChar, widthWeight = 0.9f, isFunctional = true, action = KeyAction.Comma),
            KeyboardKey.create(
                character = "🌐",
                hintLabel = language.code.uppercase(),
                widthWeight = 0.95f,
                isFunctional = true,
                action = KeyAction.GlobeSwitch
            ),
            KeyboardKey.create(
                character = language.nativeName,
                widthWeight = 4.3f,
                isFunctional = false,
                action = KeyAction.Space
            ),
            KeyboardKey.create(periodChar, widthWeight = 0.9f, isFunctional = true, action = KeyAction.Period),
            KeyboardKey.create(enterLabel, widthWeight = 1.45f, isFunctional = true, isAccent = true, action = KeyAction.Enter)
        )
    }
}
