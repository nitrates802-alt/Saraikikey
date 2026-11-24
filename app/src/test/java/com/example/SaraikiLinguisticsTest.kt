package com.example

import com.example.core.ime.KeyAction
import com.example.core.ime.KeyboardLayoutDefinitions
import com.example.core.ime.KeyboardPage
import com.example.core.ime.ShiftState
import com.example.core.linguistics.LinguisticEngine
import com.example.core.linguistics.SaraikiCharacterConfig
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaraikiLinguisticsTest {

    @Test
    fun `saraiki mandatory popup geometry matches Gboard spatial specification`() {
        val config = SaraikiCharacterConfig.defaultConfig()

        // Single centered alternates
        assertEquals(listOf("ڄ"), config.getPopupFor("ج")?.items)
        assertEquals(0, config.getPopupFor("ج")?.centerIndex)

        assertEquals(listOf("ڋ"), config.getPopupFor("د")?.items)
        assertEquals(0, config.getPopupFor("د")?.centerIndex)

        assertEquals(listOf("ݨ"), config.getPopupFor("ن")?.items)
        assertEquals(0, config.getPopupFor("ن")?.centerIndex)

        assertEquals(listOf("ٻ"), config.getPopupFor("ب")?.items)
        assertEquals(0, config.getPopupFor("ب")?.centerIndex)

        // Dual-position rule for گ: ڳ centered (index 1), ڱ immediately left (index 0)
        val gafPopup = config.getPopupFor("گ")
        assertNotNull(gafPopup)
        assertEquals(listOf("ڱ", "ڳ"), gafPopup!!.items)
        assertEquals(1, gafPopup.centerIndex)
        assertEquals("ڳ", gafPopup.items[gafPopup.centerIndex])
        assertEquals("ڱ", gafPopup.items[gafPopup.centerIndex - 1])
    }

    @Test
    fun `top row exposes Western Arabic numerals 1 to 0 across all languages with 4 char rows plus bottom bar`() {
        val config = SaraikiCharacterConfig.defaultConfig()
        val expectedDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        for (lang in listOf(KeyboardLanguage.SARAIKI, KeyboardLanguage.URDU, KeyboardLanguage.ENGLISH)) {
            val rows = KeyboardLayoutDefinitions.buildRows(
                language = lang,
                page = KeyboardPage.LETTERS,
                shiftState = ShiftState.OFF,
                showNumberRow = false,
                ergonomicMode = ErgonomicMode.NORMAL,
                imeOptions = 0,
                saraikiConfig = config
            )
            val topDigits = rows.first().mapNotNull { it.longPressAlternatives.firstOrNull() }
            assertEquals("Top row digits mismatch for $lang", expectedDigits, topDigits)
            assertTrue(rows.first().all { it.keyCode == it.character.codePointAt(0) })
            // Bottom functional row has Space + Enter
            assertTrue(rows.last().any { it.action == KeyAction.Space })
            assertTrue(rows.last().any { it.action == KeyAction.Enter })
        }

        // Saraiki and Urdu have 4 primary character rows + 1 bottom functional row = 5 rows
        val srkRows = KeyboardLayoutDefinitions.buildRows(
            KeyboardLanguage.SARAIKI, KeyboardPage.LETTERS, ShiftState.OFF, false, ErgonomicMode.NORMAL, 0, config
        )
        assertEquals(5, srkRows.size)
    }

    @Test
    fun `western arabic numerals are preserved and never converted to eastern arabic`() {
        val input = "1234567890 ۱۲۳۴۵۶۷۸۹۰"
        val normalized = SaraikiCharacterConfig.ensureWesternArabicDigits(input)
        assertEquals("1234567890 1234567890", normalized)
    }

    @Test
    fun `real time word learning predicts coined Saraiki word on the very next sentence`() {
        val engine = LinguisticEngine(repository = null)
        engine.seedForTesting(KeyboardLanguage.SARAIKI, listOf("ملتان" to 900, "سرائیکی" to 950))

        // User coins a brand-new regional Saraiki word
        engine.onWordCommitted(
            rawWord = "چولستانی",
            prevWord = "ساڋا",
            prev2Word = null,
            activeLanguage = KeyboardLanguage.SARAIKI,
            learnEnabled = true,
            isIncognito = false
        )

        // Immediately predict on the very next sentence without restart
        val predictions = engine.predictCandidates(
            composing = "چولس",
            prevWord = null,
            prev2Word = null,
            activeLanguage = KeyboardLanguage.SARAIKI,
            enabledLanguages = listOf(KeyboardLanguage.SARAIKI, KeyboardLanguage.URDU, KeyboardLanguage.ENGLISH),
            multilingualEnabled = true,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true
        )
        assertTrue(predictions.any { it.text == "چولستانی" && it.isLearned })
    }

    @Test
    fun `typo protection filter evicts newly coined word on immediate backspace and forget removes learned word`() {
        val engine = LinguisticEngine(repository = null)

        // User accidentally commits a typo and hits backspace immediately
        engine.onWordCommitted(
            rawWord = "xyztypo",
            prevWord = null,
            prev2Word = null,
            activeLanguage = KeyboardLanguage.ENGLISH,
            learnEnabled = true,
            isIncognito = false
        )
        assertTrue(engine.onImmediateBackspaceAfterCommit())

        val afterTypo = engine.predictCandidates(
            composing = "xyzt",
            prevWord = null,
            prev2Word = null,
            activeLanguage = KeyboardLanguage.ENGLISH,
            enabledLanguages = listOf(KeyboardLanguage.ENGLISH),
            multilingualEnabled = true,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true
        )
        assertFalse(afterTypo.any { it.text == "xyztypo" && it.isLearned })

        // Now confirm a valid learned word and then forget it
        engine.onWordCommitted("Multani", null, null, KeyboardLanguage.ENGLISH, learnEnabled = true, isIncognito = false, confirmedBySuggestionTap = true)
        engine.forgetLearnedWord("Multani", KeyboardLanguage.ENGLISH)
        val afterForget = engine.predictCandidates(
            composing = "Mult",
            prevWord = null,
            prev2Word = null,
            activeLanguage = KeyboardLanguage.ENGLISH,
            enabledLanguages = listOf(KeyboardLanguage.ENGLISH),
            multilingualEnabled = false,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true
        )
        assertFalse(afterForget.any { it.text == "Multani" && it.isLearned })
    }

    @Test
    fun `incognito mode prevents learning new words`() {
        val engine = LinguisticEngine(repository = null)
        engine.onWordCommitted(
            rawWord = "SecretPinWord",
            prevWord = null,
            prev2Word = null,
            activeLanguage = KeyboardLanguage.ENGLISH,
            learnEnabled = true,
            isIncognito = true
        )
        val predictions = engine.predictCandidates(
            composing = "SecretP",
            prevWord = null,
            prev2Word = null,
            activeLanguage = KeyboardLanguage.ENGLISH,
            enabledLanguages = listOf(KeyboardLanguage.ENGLISH),
            multilingualEnabled = true,
            autocorrectEnabled = true,
            nextWordPredictionEnabled = true
        )
        assertFalse(predictions.any { it.text == "SecretPinWord" && it.isLearned })
    }
}
