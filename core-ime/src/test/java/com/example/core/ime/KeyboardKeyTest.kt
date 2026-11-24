package com.example.core.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardKeyTest {

    @Test
    fun `keyboard key defaults optional alternatives and resolves unicode key code`() {
        val key = KeyboardKey(character = "ٻ")
        assertEquals("ٻ", key.character)
        assertEquals("ٻ".codePointAt(0), key.keyCode)
        assertTrue(key.longPressAlternatives.isEmpty())
        assertEquals(1f, key.widthWeight, 0.001f)
        assertEquals(1f, key.heightWeight, 0.001f)
    }

    @Test
    fun `keyboard key stores long press alternatives and visual layout dimensions`() {
        val key = KeyboardKey.create(
            character = "گ",
            longPressAlternatives = listOf("ڱ", "ڳ"),
            longPressCenterIndex = 1,
            widthWeight = 1.4f,
            heightWeight = 1.1f
        ).withBounds(x = 40f, y = 200f, width = 100f, height = 120f)

        assertEquals(listOf("ڱ", "ڳ"), key.longPressAlternatives)
        assertEquals(1, key.longPressCenterIndex)
        assertEquals(1.4f, key.widthWeight, 0.001f)
        assertEquals(1.1f, key.heightWeight, 0.001f)
        assertEquals(140f, key.right, 0.001f)
        assertEquals(320f, key.bottom, 0.001f)
        assertEquals(90f, key.centerX, 0.001f)
        assertEquals(260f, key.centerY, 0.001f)
        assertTrue(key.contains(85f, 250f))
        assertFalse(key.contains(140f, 250f))
    }

    @Test
    fun `functional key actions resolve expected key codes`() {
        assertEquals(KeyboardKey.KEYCODE_SHIFT, KeyboardKey.create("⇧", action = KeyAction.Shift).keyCode)
        assertEquals(KeyboardKey.KEYCODE_DELETE, KeyboardKey.create("⌫", action = KeyAction.Backspace).keyCode)
        assertEquals(KeyboardKey.KEYCODE_SPACE, KeyboardKey.create("سرائیکی", action = KeyAction.Space).keyCode)
        assertEquals(KeyboardKey.KEYCODE_ENTER, KeyboardKey.create("↵", action = KeyAction.Enter).keyCode)
    }
}
