package com.example.core.ime

import com.example.core.database.FakeKeyboardDao
import com.example.core.database.WordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing prefix retrieves matching learned words and clears suggestions on commit`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val dao = FakeKeyboardDao()
        val repository = WordRepository(dao, ioDispatcher = testDispatcher)
        repository.insertOrIncrementWord("چولستان", "srk", timestamp = 100L)
        repository.insertOrIncrementWord("چولستانی", "srk", timestamp = 120L)
        repository.insertOrIncrementWord("چولستانی", "srk", timestamp = 130L)
        repository.insertOrIncrementWord("ملتان", "srk", timestamp = 140L)

        val viewModel = KeyboardViewModel(repository, initialLanguage = "srk")
        advanceUntilIdle()

        assertEquals("srk", viewModel.uiState.value.activeLanguage)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertEquals(3, viewModel.uiState.value.learnedWords.size)

        viewModel.appendText("چول")
        advanceUntilIdle()

        assertEquals("چول", viewModel.uiState.value.composingPrefix)
        assertEquals(
            listOf("چولستانی", "چولستان"),
            viewModel.uiState.value.suggestions.map { it.word }
        )

        val topSuggestion = viewModel.uiState.value.suggestions.first()
        viewModel.selectSuggestion(topSuggestion)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.composingPrefix)
        assertEquals("چولستانی", viewModel.uiState.value.lastCommittedWord)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertEquals(3, repository.findWord("srk", "چولستانی")?.frequencyCount)
    }

    @Test
    fun `commitComposingWord saves new word and language switch isolates vocabulary`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val dao = FakeKeyboardDao()
        val repository = WordRepository(dao, ioDispatcher = testDispatcher)
        val viewModel = KeyboardViewModel(repository, initialLanguage = "srk")
        advanceUntilIdle()

        viewModel.appendCharacter('ٻ')
        viewModel.appendCharacter('ا')
        viewModel.appendCharacter('ل')
        viewModel.commitComposingWord()
        advanceUntilIdle()

        assertEquals("ٻال", viewModel.uiState.value.lastCommittedWord)
        assertEquals(1, repository.findWord("srk", "ٻال")?.frequencyCount)

        viewModel.setLanguage("eng")
        viewModel.onComposingPrefixChanged("hel")
        viewModel.backspacePrefix()
        advanceUntilIdle()
        assertEquals("he", viewModel.uiState.value.composingPrefix)

        viewModel.commitTypedWord("hello")
        advanceUntilIdle()

        assertEquals("eng", viewModel.uiState.value.activeLanguage)
        assertEquals(listOf("hello"), viewModel.uiState.value.learnedWords.map { it.word })

        viewModel.onComposingPrefixChanged("he")
        advanceUntilIdle()
        assertEquals(listOf("hello"), viewModel.uiState.value.suggestions.map { it.word })
    }

    @Test
    fun `write failure surfaces errorMessage until cleared`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)

        val dao = FakeKeyboardDao().apply { failNextInsert = true }
        val repository = WordRepository(dao, ioDispatcher = testDispatcher)
        val viewModel = KeyboardViewModel(repository, initialLanguage = "srk")
        advanceUntilIdle()

        viewModel.commitTypedWord("غلط")
        advanceUntilIdle()

        assertEquals("Simulated Room write error", viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
