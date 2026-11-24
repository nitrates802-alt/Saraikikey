package com.example

import com.example.core.database.LearnedWordEntity
import com.example.core.ime.KeyAction
import com.example.core.ime.KeyboardLayoutDefinitions
import com.example.core.ime.KeyboardPage
import com.example.core.ime.ShiftState
import com.example.core.linguistics.DictionaryIoManager
import com.example.core.linguistics.GlidePoint
import com.example.core.linguistics.KeyCenter
import com.example.core.linguistics.LinguisticEngine
import com.example.core.linguistics.PrefixTrie
import com.example.core.linguistics.SaraikiCharacterConfig
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryAndThemeUnitTest {

    @Test
    fun `json and csv dictionary export and import round trip accurately`() {
        val sample = listOf(
            LearnedWordEntity(id = 1, word = "چولستانی", language = KeyboardLanguage.SARAIKI.code, frequencyCount = 4, isUserCoined = true),
            LearnedWordEntity(id = 2, word = "شکریہ", language = KeyboardLanguage.URDU.code, frequencyCount = 2, isUserCoined = false),
            LearnedWordEntity(id = 3, word = "Multan", language = KeyboardLanguage.ENGLISH.code, frequencyCount = 5, isUserCoined = true)
        )

        val json = DictionaryIoManager.exportAsJson(sample)
        val fromJson = DictionaryIoManager.importFromText(json, KeyboardLanguage.SARAIKI)
        assertEquals(3, fromJson.size)
        assertEquals("چولستانی" to KeyboardLanguage.SARAIKI, fromJson[0])

        val csv = DictionaryIoManager.exportAsCsv(sample)
        val fromCsv = DictionaryIoManager.importFromText(csv, KeyboardLanguage.SARAIKI)
        assertEquals(3, fromCsv.size)
        assertEquals("Multan" to KeyboardLanguage.ENGLISH, fromCsv[2])
    }

    @Test
    fun `prefix trie frecency ranks recently learned words above stale words`() {
        val trie = PrefixTrie(caseInsensitive = false)
        val now = 1_700_000_000_000L
        trie.insertOrUpdateBase("ملتان", 500)
        trie.recordLearned("ملتاݨی", increment = 3, timestamp = now, isUserCoined = true)

        val results = trie.prefixSearch("ملتا", limit = 5, nowMs = now)
        assertEquals(2, results.size)
        assertEquals("ملتاݨی", results.first().word)
    }

    @Test
    fun `damerau levenshtein handles single transposition and substitution`() {
        assertEquals(1, PrefixTrie.damerauLevenshtein("teh", "the", 2))
        assertEquals(1, PrefixTrie.damerauLevenshtein("multn", "multan", 2))
    }

    @Test
    fun `glide path decoder matches start to end spatial path`() {
        val engine = LinguisticEngine(repository = null)
        engine.seedForTesting(KeyboardLanguage.ENGLISH, listOf("hello" to 900, "world" to 850))

        val centers = listOf(
            KeyCenter("h", 100f, 100f),
            KeyCenter("e", 200f, 50f),
            KeyCenter("l", 300f, 100f),
            KeyCenter("o", 400f, 50f)
        )
        val path = listOf(
            GlidePoint(100f, 100f, 0L),
            GlidePoint(200f, 50f, 40L),
            GlidePoint(300f, 100f, 80L),
            GlidePoint(400f, 50f, 120L)
        )
        val decoded = engine.decodeGlidePath(path, centers, KeyboardLanguage.ENGLISH)
        assertTrue(decoded.any { it.text == "hello" })
    }

    @Test
    fun `optional number row and split tablet spacer apply cleanly`() {
        val config = SaraikiCharacterConfig.defaultConfig()
        val withNumRow = KeyboardLayoutDefinitions.buildRows(
            KeyboardLanguage.SARAIKI, KeyboardPage.LETTERS, ShiftState.OFF,
            showNumberRow = true, ergonomicMode = ErgonomicMode.SPLIT_TABLET, imeOptions = 0, saraikiConfig = config
        )
        assertEquals(6, withNumRow.size)
        assertTrue(withNumRow.first().any { it.character == "1" })
        assertTrue(withNumRow[1].any { it.action == KeyAction.Spacer })
    }

    @Test
    fun `keyboard key captures character, keyCode, alternatives, and visual dimensions`() {
        val baseKey = com.example.core.ime.KeyboardKey.create(
            character = "گ",
            longPressAlternatives = listOf("ڱ", "ڳ"),
            longPressCenterIndex = 1,
            widthWeight = 1.2f
        )
        assertEquals("گ", baseKey.character)
        assertEquals("گ".codePointAt(0), baseKey.keyCode)
        assertEquals(listOf("ڱ", "ڳ"), baseKey.longPressAlternatives)
        assertEquals(1.2f, baseKey.widthWeight, 0.001f)

        val positioned = baseKey.withBounds(x = 120f, y = 80f, width = 90f, height = 110f)
        assertEquals(165f, positioned.centerX, 0.001f)
        assertEquals(135f, positioned.centerY, 0.001f)
        assertTrue(positioned.contains(150f, 100f))
    }
}
