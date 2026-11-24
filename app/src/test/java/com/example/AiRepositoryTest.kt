package com.example

import com.example.core.ai.AiTaskType
import com.example.core.ai.PlaceholderAiRepository
import com.example.core.preferences.KeyboardLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRepositoryTest {

    @Test
    fun `placeholder ai repository returns labeled sample responses and firebase notice for all tasks`() = runTest {
        val repo = PlaceholderAiRepository()

        for (task in AiTaskType.entries) {
            for (lang in KeyboardLanguage.entries) {
                val result = repo.runTask(task, "السلام علیکم", lang)
                assertTrue(result.isPlaceholder)
                assertTrue(result.outputText.startsWith("[Sample AI Response]"))
                assertTrue(result.notice.contains("Firebase", ignoreCase = true))
            }
        }
    }
}
