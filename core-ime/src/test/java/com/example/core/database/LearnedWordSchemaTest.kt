package com.example.core.database

import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedWordSchemaTest {

    @Test
    fun `ksp generates room database and dao implementations in core-ime`() {
        assertTrue(RoomDatabase::class.java.isAssignableFrom(SaraikiKeyboardDatabase::class.java))
        assertTrue(SaraikiKeyboardDatabase::class.java.isAssignableFrom(SaraikiKeyboardDatabase_Impl::class.java))
        assertTrue(KeyboardDao::class.java.isAssignableFrom(KeyboardDao_Impl::class.java))

        val observePrefix = KeyboardDao::class.java.getDeclaredMethod(
            "observePrefixMatches",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        )
        assertEquals(Flow::class.java, observePrefix.returnType)
    }

    @Test
    fun `learned word entity stores word, language, frequency, and timestamp`() {
        val entry = LearnedWordEntity(
            word = "چولستانی",
            language = "srk",
            frequencyCount = 7,
            lastUsedTimestamp = 1_700_000_000_000L,
            isUserCoined = true
        )
        assertEquals("چولستانی", entry.word)
        assertEquals("srk", entry.language)
        assertEquals(7, entry.frequencyCount)
        assertEquals(1_700_000_000_000L, entry.lastUsedTimestamp)
        assertTrue(entry.isUserCoined)
    }
}
