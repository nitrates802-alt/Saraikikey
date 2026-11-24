package com.example.core.ime

import android.widget.LinearLayout
import com.example.core.database.LearnedWordEntity
import com.example.core.database.WordRepository
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateViewTest {

    @Test
    fun `candidate view extends LinearLayout and exposes WordRepository query methods`() {
        val viewClass = CandidateView::class.java
        assertTrue(LinearLayout::class.java.isAssignableFrom(viewClass))

        val bindMethod = viewClass.getDeclaredMethod(
            "bindWordRepository",
            WordRepository::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            CoroutineDispatcher::class.java
        )
        assertEquals(Void.TYPE, bindMethod.returnType)

        val updateQueryMethod = viewClass.getDeclaredMethod(
            "updateQuery",
            String::class.java,
            String::class.java
        )
        assertEquals(Void.TYPE, updateQueryMethod.returnType)

        val setCandidatesMethod = viewClass.getDeclaredMethod(
            "setCandidates",
            List::class.java
        )
        assertEquals(Void.TYPE, setCandidatesMethod.returnType)

        val applyThemeMethod = viewClass.getDeclaredMethod(
            "applyTheme",
            ResolvedKeyboardTheme::class.java
        )
        assertEquals(Void.TYPE, applyThemeMethod.returnType)
    }

    @Test
    fun `saraiki keyboard service exposes candidateView, WordRepository factory, and candidate handler`() {
        val serviceClass = SaraikiKeyboardService::class.java

        val candidateGetter = serviceClass.getDeclaredMethod("getCandidateView")
        assertEquals(CandidateView::class.java, candidateGetter.returnType)

        val repoFactory = serviceClass.getDeclaredMethod("createWordRepository")
        assertEquals(WordRepository::class.java, repoFactory.returnType)

        val onCandidateChosen = serviceClass.getDeclaredMethod(
            "onCandidateChosen",
            LearnedWordEntity::class.java
        )
        assertEquals(Void.TYPE, onCandidateChosen.returnType)
    }
}
