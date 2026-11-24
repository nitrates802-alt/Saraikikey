package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.ClipboardEntryEntity
import com.example.core.database.SaraikiKeyboardDatabase
import com.example.core.preferences.KeyboardLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseAndSettingsRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `room database persists learned words, ngrams, and clipboard snippets locally`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SaraikiKeyboardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.keyboardDao()

        dao.recordLearnedWord("ڳالھ", KeyboardLanguage.SARAIKI.code, isUserCoined = true)
        dao.recordNGram(KeyboardLanguage.SARAIKI.code, "سوہݨی", "ڳالھ")
        dao.upsertClipboardItem(ClipboardEntryEntity(text = "السلام علیکم", isPinned = true))

        val words = dao.getAllLearnedWordsSync()
        assertEquals(1, words.size)
        assertEquals("ڳالھ", words.first().word)

        val ngrams = dao.getAllNGramsSync()
        assertEquals(1, ngrams.size)
        assertEquals("ڳالھ", ngrams.first().nextWord)

        val clips = dao.getClipboardItemsSync()
        assertEquals(1, clips.size)
        assertTrue(clips.first().isPinned)

        dao.clearAllLearnedWords()
        assertTrue(dao.getAllLearnedWordsSync().isEmpty())
        db.close()
    }

    @Test
    fun `main activity launches setup wizard cleanly on unconfigured device`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wizard_step1_button").assertIsDisplayed()
        composeRule.onNodeWithTag("wizard_test_input").assertIsDisplayed()
    }
}
