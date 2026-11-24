package com.example.core.database

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordRepositoryTest {

    @Test
    fun `insertOrIncrementWord trims input, increments frequency, and records ngram`() = runTest {
        val dao = FakeKeyboardDao()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = WordRepository(dao, ioDispatcher = dispatcher)

        repository.insertOrIncrementWord(
            word = "  ملتان  ",
            language = "srk",
            isUserCoined = true,
            previousWord = "سوہݨاں",
            timestamp = 100L
        )
        repository.insertOrIncrementWord(
            word = "ملتان",
            language = "srk",
            isUserCoined = false,
            previousWord = "سوہݨاں",
            timestamp = 200L
        )

        val stored = repository.findWord("srk", "ملتان")
        assertNotNull(stored)
        assertEquals(2, stored?.frequencyCount)
        assertEquals(200L, stored?.lastUsedTimestamp)
        assertTrue(stored?.isUserCoined == true)

        val ngrams = dao.getAllNGramsSync()
        assertEquals(1, ngrams.size)
        assertEquals("سوہݨاں", ngrams.first().contextPrefix)
        assertEquals("ملتان", ngrams.first().nextWord)
        assertEquals(2, ngrams.first().transitionCount)
    }

    @Test
    fun `prefix retrieval orders by frequency and returns empty for blank prefix`() = runTest {
        val dao = FakeKeyboardDao()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = WordRepository(dao, ioDispatcher = dispatcher)

        repository.insertOrIncrementWord("سرائیکستان", "srk", timestamp = 10L)
        repository.insertOrIncrementWord("سرائیکی", "srk", timestamp = 20L)
        repository.insertOrIncrementWord("سرائیکی", "srk", timestamp = 30L)
        repository.insertOrIncrementWord("سوہݨاں", "srk", timestamp = 40L)

        assertTrue(repository.findPrefixSuggestions("srk", "   ").isEmpty())
        assertTrue(repository.observePrefixSuggestions("srk", "").first().isEmpty())

        val matches = repository.findPrefixSuggestions("srk", "سرا")
        assertEquals(listOf("سرائیکی", "سرائیکستان"), matches.map { it.word })

        val observed = repository.observePrefixSuggestions("srk", "سرا", limit = 1).first()
        assertEquals(listOf("سرائیکی"), observed.map { it.word })
    }

    @Test
    fun `deleteWord removes learned word and associated ngrams`() = runTest {
        val dao = FakeKeyboardDao()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = WordRepository(dao, ioDispatcher = dispatcher)

        repository.insertOrIncrementWord("سلام", "srk", previousWord = "صبح")
        assertNotNull(repository.findWord("srk", "سلام"))
        assertEquals(1, dao.getAllNGramsSync().size)

        repository.deleteWord("srk", "سلام")
        assertNull(repository.findWord("srk", "سلام"))
        assertTrue(dao.getAllNGramsSync().isEmpty())
    }
}
