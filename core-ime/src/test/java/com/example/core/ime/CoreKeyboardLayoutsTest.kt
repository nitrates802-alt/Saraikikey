package com.example.core.ime

import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreKeyboardLayoutsTest {

    @Test
    fun `ime language cycles Saraiki to Urdu to English and back to Saraiki`() {
        assertEquals(ImeLanguage.URDU, ImeLanguage.SARAIKI.next())
        assertEquals(ImeLanguage.ENGLISH, ImeLanguage.URDU.next())
        assertEquals(ImeLanguage.SARAIKI, ImeLanguage.ENGLISH.next())

        assertEquals(ImeLanguage.SARAIKI, ImeLanguage.fromCode("srk"))
        assertEquals(ImeLanguage.URDU, ImeLanguage.fromCode("urd"))
        assertEquals(ImeLanguage.ENGLISH, ImeLanguage.fromCode("eng"))
    }

    @Test
    fun `all three language layouts include dedicated globe switch key and language specific keys`() {
        val saraikiRows = CoreKeyboardLayouts.buildRows(ImeLanguage.SARAIKI)
        val urduRows = CoreKeyboardLayouts.buildRows(ImeLanguage.URDU)
        val englishRows = CoreKeyboardLayouts.buildRows(ImeLanguage.ENGLISH)

        val saraikiKeys = saraikiRows.flatten()
        val urduKeys = urduRows.flatten()
        val englishKeys = englishRows.flatten()

        assertTrue(saraikiKeys.any { it.character == "ق" })
        assertTrue(saraikiKeys.any { it.longPressAlternatives.contains("ٻ") })
        assertTrue(urduKeys.any { it.character == "ق" })
        assertTrue(englishKeys.any { it.character == "q" })
        assertFalse(englishKeys.any { it.character == "ق" })

        listOf(
            ImeLanguage.SARAIKI to saraikiRows.last(),
            ImeLanguage.URDU to urduRows.last(),
            ImeLanguage.ENGLISH to englishRows.last()
        ).forEach { (lang, bottomRow) ->
            val globeKey = bottomRow.firstOrNull { it.action == KeyAction.GlobeSwitch }
            assertNotNull("Expected globe key for ${lang.name}", globeKey)
            assertEquals(KeyboardKey.KEYCODE_GLOBE, globeKey?.keyCode)
            assertEquals("🌐", globeKey?.character)
            assertEquals(lang.code.uppercase(), globeKey?.hintLabel)

            val spaceKey = bottomRow.firstOrNull { it.action == KeyAction.Space }
            assertEquals(lang.nativeName, spaceKey?.character)
        }
    }

    @Test
    fun `saraiki keyboard service exposes language toggle methods and state flow`() {
        val serviceClass = SaraikiKeyboardService::class.java

        val toggleMethod = serviceClass.getDeclaredMethod("toggleNextLanguage")
        assertEquals(ImeLanguage::class.java, toggleMethod.returnType)

        val switchMethod = serviceClass.getDeclaredMethod("switchLanguage", ImeLanguage::class.java)
        assertEquals(ImeLanguage::class.java, switchMethod.returnType)

        val activeLangGetter = serviceClass.getDeclaredMethod("getActiveLanguage")
        assertEquals(ImeLanguage::class.java, activeLangGetter.returnType)

        val activeLangFlowGetter = serviceClass.getDeclaredMethod("getActiveLanguageFlow")
        assertEquals(StateFlow::class.java, activeLangFlowGetter.returnType)

        val handleKeyActionMethod = serviceClass.getDeclaredMethod("handleDefaultKeyAction", KeyAction::class.java)
        assertEquals(Void.TYPE, handleKeyActionMethod.returnType)
    }
}
