package com.example.core.ime

import android.view.inputmethod.EditorInfo
import com.example.core.linguistics.PopupSpec
import com.example.core.linguistics.SaraikiCharacterConfig
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardLanguage

enum class ShiftState { OFF, SHIFTED, CAPS_LOCK }

enum class KeyboardPage { LETTERS, SYMBOLS_1, SYMBOLS_2 }

object KeyboardLayoutDefinitions {

    private val QUICK_PUNCTUATION_RTL = listOf("؟", "!", "،", "؛", ":", "\"", "'", "(", ")")
    private val QUICK_PUNCTUATION_LTR = listOf("?", "!", ",", ";", ":", "\"", "'", "(", ")")

    fun buildRows(
        language: KeyboardLanguage,
        page: KeyboardPage,
        shiftState: ShiftState,
        showNumberRow: Boolean,
        ergonomicMode: ErgonomicMode,
        imeOptions: Int,
        saraikiConfig: SaraikiCharacterConfig
    ): List<List<KeyboardKey>> {
        val rows = mutableListOf<List<KeyboardKey>>()

        if (showNumberRow && page == KeyboardPage.LETTERS) {
            rows.add(buildNumberRow())
        }

        when (page) {
            KeyboardPage.LETTERS -> {
                when (language) {
                    KeyboardLanguage.SARAIKI -> rows.addAll(buildSaraikiRows(shiftState, saraikiConfig))
                    KeyboardLanguage.URDU -> rows.addAll(buildUrduRows(shiftState))
                    KeyboardLanguage.ENGLISH -> rows.addAll(buildEnglishRows(shiftState))
                }
            }
            KeyboardPage.SYMBOLS_1 -> rows.addAll(buildSymbols1Rows(language))
            KeyboardPage.SYMBOLS_2 -> rows.addAll(buildSymbols2Rows(language))
        }

        val splitRows = if (ergonomicMode == ErgonomicMode.SPLIT_TABLET) {
            rows.map { row -> injectSplitGap(row) }
        } else rows

        val bottomRow = buildBottomFunctionalRow(language, page, imeOptions, ergonomicMode)
        return splitRows + listOf(bottomRow)
    }

    private fun buildNumberRow(): List<KeyboardKey> =
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { digit ->
            KeyboardKey.create(
                character = digit,
                widthWeight = 1f,
                action = KeyAction.CommitChar(digit)
            )
        }

    private fun buildSaraikiRows(
        shiftState: ShiftState,
        config: SaraikiCharacterConfig
    ): List<List<KeyboardKey>> {
        if (shiftState != ShiftState.OFF) {
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

        // Row 1: 10 keys with Western Arabic numerals 1..0 strictly as long-press alternates
        val row1Chars = listOf("ق", "و", "ع", "ر", "ت", "ے", "ی", "ء", "ہ", "پ")
        val row1Digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val r1 = row1Chars.mapIndexed { index, ch ->
            val digit = row1Digits[index]
            KeyboardKey.create(
                character = ch,
                hintLabel = digit,
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = listOf(digit),
                longPressCenterIndex = 0
            )
        }

        // Row 2: 10 keys (د->ڋ centered, گ->ڳ centered + ڱ left, ج->ڄ centered)
        val row2Chars = listOf("ا", "س", "د", "ف", "گ", "ح", "ج", "ک", "ل", "ط")
        val r2 = row2Chars.map { ch ->
            val spec: PopupSpec? = config.getPopupFor(ch)
            KeyboardKey.create(
                character = ch,
                hintLabel = spec?.items?.getOrNull(spec.centerIndex),
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = spec?.items.orEmpty(),
                longPressCenterIndex = spec?.centerIndex ?: 0
            )
        }

        // Row 3: 10 keys (چ->ݮ centered, ب->ٻ centered, ن->ݨ centered)
        val row3Chars = listOf("ز", "ش", "چ", "خ", "غ", "ب", "ن", "م", "ڑ", "ڈ")
        val r3 = row3Chars.map { ch ->
            val spec: PopupSpec? = config.getPopupFor(ch)
            KeyboardKey.create(
                character = ch,
                hintLabel = spec?.items?.getOrNull(spec.centerIndex),
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = spec?.items.orEmpty(),
                longPressCenterIndex = spec?.centerIndex ?: 0
            )
        }

        // Row 4: Shift + 8 letters + Backspace
        val row4Chars = listOf("ٹ", "ص", "ض", "ذ", "ث", "ظ", "ں", "ھ")
        val r4 = buildList {
            add(KeyboardKey.create("⇧", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Shift))
            row4Chars.forEach { ch ->
                add(KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Backspace))
        }

        return listOf(r1, r2, r3, r4)
    }

    private fun buildUrduRows(shiftState: ShiftState): List<List<KeyboardKey>> {
        if (shiftState != ShiftState.OFF) {
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

        val urduPopups = mapOf(
            "ا" to PopupSpec(listOf("آ", "أ", "إ"), 1),
            "د" to PopupSpec(listOf("ڈ", "ذ"), 0),
            "ج" to PopupSpec(listOf("چ"), 0),
            "گ" to PopupSpec(listOf("غ"), 0),
            "ن" to PopupSpec(listOf("ں"), 0),
            "س" to PopupSpec(listOf("ص"), 0),
            "ز" to PopupSpec(listOf("ژ", "ض"), 0),
            "ط" to PopupSpec(listOf("ظ"), 0),
            "ح" to PopupSpec(listOf("خ"), 0)
        )

        val row1Chars = listOf("ق", "و", "ع", "ر", "ت", "ے", "ی", "ء", "ہ", "پ")
        val row1Digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val r1 = row1Chars.mapIndexed { idx, ch ->
            KeyboardKey.create(ch, hintLabel = row1Digits[idx], action = KeyAction.CommitChar(ch), longPressAlternatives = listOf(row1Digits[idx]), longPressCenterIndex = 0)
        }
        val r2 = listOf("ا", "س", "د", "ف", "گ", "ح", "ج", "ک", "ل", "ط").map { ch ->
            val spec = urduPopups[ch]
            KeyboardKey.create(ch, hintLabel = spec?.items?.getOrNull(spec.centerIndex), action = KeyAction.CommitChar(ch), longPressAlternatives = spec?.items.orEmpty(), longPressCenterIndex = spec?.centerIndex ?: 0)
        }
        val r3 = listOf("ز", "ش", "چ", "خ", "غ", "ب", "ن", "م", "ڑ", "ڈ").map { ch ->
            val spec = urduPopups[ch]
            KeyboardKey.create(ch, hintLabel = spec?.items?.getOrNull(spec.centerIndex), action = KeyAction.CommitChar(ch), longPressAlternatives = spec?.items.orEmpty(), longPressCenterIndex = spec?.centerIndex ?: 0)
        }
        val r4 = buildList {
            add(KeyboardKey.create("⇧", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Shift))
            listOf("ٹ", "ص", "ض", "ذ", "ث", "ظ", "ں", "ھ").forEach { ch ->
                add(KeyboardKey.create(ch, action = KeyAction.CommitChar(ch)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.4f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3, r4)
    }

    private fun buildEnglishRows(shiftState: ShiftState): List<List<KeyboardKey>> {
        val upper = shiftState != ShiftState.OFF
        val row1Letters = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row1Digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val r1 = row1Letters.mapIndexed { idx, raw ->
            val ch = if (upper) raw.uppercase() else raw
            KeyboardKey.create(
                character = ch,
                hintLabel = row1Digits[idx],
                action = KeyAction.CommitChar(ch),
                longPressAlternatives = listOf(row1Digits[idx]),
                longPressCenterIndex = 0
            )
        }

        val row2Letters = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val r2 = row2Letters.map { raw ->
            val ch = if (upper) raw.uppercase() else raw
            KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch))
        }

        val row3Letters = listOf("z", "x", "c", "v", "b", "n", "m")
        val shiftLabel = when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.SHIFTED -> "⬆"
            ShiftState.CAPS_LOCK -> "⇪"
        }
        val r3 = buildList {
            add(
                KeyboardKey.create(
                    character = shiftLabel,
                    widthWeight = 1.5f,
                    isFunctional = true,
                    isAccent = shiftState != ShiftState.OFF,
                    action = KeyAction.Shift
                )
            )
            row3Letters.forEach { raw ->
                val ch = if (upper) raw.uppercase() else raw
                add(KeyboardKey.create(character = ch, action = KeyAction.CommitChar(ch)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.5f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3)
    }

    private fun buildSymbols1Rows(language: KeyboardLanguage): List<List<KeyboardKey>> {
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map {
            KeyboardKey.create(it, action = KeyAction.CommitChar(it))
        }
        val r2 = listOf("@", "#", "₨", "_", "&", "-", "+", "(", ")", "/").map {
            KeyboardKey.create(it, action = KeyAction.CommitChar(it))
        }
        val qMark = if (language.isRtl) "؟" else "?"
        val r3 = buildList {
            add(KeyboardKey.create("=\\<", widthWeight = 1.5f, isFunctional = true, action = KeyAction.ToggleMoreSymbols))
            listOf("*", "\"", "'", ":", ";", "!", qMark).forEach {
                add(KeyboardKey.create(it, action = KeyAction.CommitChar(it)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.5f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3)
    }

    private fun buildSymbols2Rows(language: KeyboardLanguage): List<List<KeyboardKey>> {
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "Δ").map {
            KeyboardKey.create(it, action = KeyAction.CommitChar(it))
        }
        val r2 = listOf("£", "€", "$", "¢", "^", "°", "=", "{", "}", "\\").map {
            KeyboardKey.create(it, action = KeyAction.CommitChar(it))
        }
        val r3 = buildList {
            add(KeyboardKey.create("?123", widthWeight = 1.5f, isFunctional = true, action = KeyAction.ToggleSymbols))
            listOf("%", "©", "®", "™", "✓", "[", "]").forEach {
                add(KeyboardKey.create(it, action = KeyAction.CommitChar(it)))
            }
            add(KeyboardKey.create("⌫", widthWeight = 1.5f, isFunctional = true, action = KeyAction.Backspace))
        }
        return listOf(r1, r2, r3)
    }

    private fun buildBottomFunctionalRow(
        language: KeyboardLanguage,
        page: KeyboardPage,
        imeOptions: Int,
        ergonomicMode: ErgonomicMode
    ): List<KeyboardKey> {
        val leftToggleLabel = if (page == KeyboardPage.LETTERS) "?123" else "ABC"
        val commaChar = if (language.isRtl) "،" else ","
        val periodChar = if (language.isRtl) "۔" else "."
        val quickPunct = if (language.isRtl) QUICK_PUNCTUATION_RTL else QUICK_PUNCTUATION_LTR

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
            KeyboardKey.create(leftToggleLabel, widthWeight = 1.35f, isFunctional = true, action = KeyAction.ToggleSymbols),
            KeyboardKey.create(commaChar, widthWeight = 0.85f, isFunctional = true, action = KeyAction.Comma),
            KeyboardKey.create("☺", widthWeight = 0.9f, isFunctional = true, action = KeyAction.EmojiDrawer),
            KeyboardKey.create("🌐", hintLabel = language.code.uppercase(), widthWeight = 0.9f, isFunctional = true, action = KeyAction.GlobeSwitch),
            KeyboardKey.create(
                character = language.nativeName,
                widthWeight = if (ergonomicMode == ErgonomicMode.SPLIT_TABLET) 2.5f else 3.6f,
                isFunctional = false,
                action = KeyAction.Space
            ),
            KeyboardKey.create(
                character = periodChar,
                hintLabel = if (language.isRtl) "؟" else "?",
                widthWeight = 0.85f,
                isFunctional = true,
                action = KeyAction.Period,
                longPressAlternatives = quickPunct,
                longPressCenterIndex = 0
            ),
            KeyboardKey.create(enterLabel, widthWeight = 1.45f, isFunctional = true, isAccent = true, action = KeyAction.Enter)
        )
    }

    private fun injectSplitGap(row: List<KeyboardKey>): List<KeyboardKey> {
        if (row.size < 6) return row
        val mid = row.size / 2
        val result = ArrayList<KeyboardKey>(row.size + 1)
        row.forEachIndexed { idx, key ->
            if (idx == mid) {
                result.add(KeyboardKey.create(character = "", widthWeight = 1.25f, isFunctional = true, action = KeyAction.Spacer))
            }
            result.add(key)
        }
        return result
    }
}
