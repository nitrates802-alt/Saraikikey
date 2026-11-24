package com.example.core.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardHapticFeedbackTest {

    @Test
    fun `haptic duration and amplitude scale appropriately per key press type`() {
        val regular = KeyboardHapticFeedback.computeDurationMs(18, KeyPressHapticType.REGULAR)
        val space = KeyboardHapticFeedback.computeDurationMs(18, KeyPressHapticType.SPACE_OR_ENTER)
        val backspace = KeyboardHapticFeedback.computeDurationMs(18, KeyPressHapticType.BACKSPACE_REPEAT)
        val tick = KeyboardHapticFeedback.computeDurationMs(18, KeyPressHapticType.POPUP_TICK)

        assertEquals(18L, regular)
        assertTrue(space > regular)
        assertTrue(backspace < regular)
        assertTrue(tick <= backspace)

        assertTrue(
            KeyboardHapticFeedback.computeAmplitude(KeyPressHapticType.SPACE_OR_ENTER) >
                KeyboardHapticFeedback.computeAmplitude(KeyPressHapticType.REGULAR)
        )
        assertTrue(
            KeyboardHapticFeedback.computeAmplitude(KeyPressHapticType.REGULAR) >
                KeyboardHapticFeedback.computeAmplitude(KeyPressHapticType.POPUP_TICK)
        )
    }

    @Test
    fun `resolveHapticType maps key actions to professional tactile categories`() {
        assertEquals(
            KeyPressHapticType.REGULAR,
            KeyboardHapticFeedback.resolveHapticType(KeyAction.CommitChar("ب"))
        )
        assertEquals(
            KeyPressHapticType.SPACE_OR_ENTER,
            KeyboardHapticFeedback.resolveHapticType(KeyAction.Space)
        )
        assertEquals(
            KeyPressHapticType.SPACE_OR_ENTER,
            KeyboardHapticFeedback.resolveHapticType(KeyAction.Enter)
        )
        assertEquals(
            KeyPressHapticType.SPACE_OR_ENTER,
            KeyboardHapticFeedback.resolveHapticType(KeyAction.GlobeSwitch)
        )
        assertEquals(
            KeyPressHapticType.BACKSPACE_REPEAT,
            KeyboardHapticFeedback.resolveHapticType(KeyAction.Backspace)
        )
        assertNull(KeyboardHapticFeedback.resolveHapticType(KeyAction.Spacer))
    }

    @Test
    fun `shouldVibrate respects enabled toggle and positive duration`() {
        assertTrue(KeyboardHapticFeedback.shouldVibrate(enabled = true, baseDurationMs = 18))
        assertFalse(KeyboardHapticFeedback.shouldVibrate(enabled = false, baseDurationMs = 18))
        assertFalse(KeyboardHapticFeedback.shouldVibrate(enabled = true, baseDurationMs = 0))
        assertFalse(KeyboardHapticFeedback.shouldVibrate(enabled = true, baseDurationMs = -5))
    }

    @Test
    fun `saraiki keyboard service and interaction listener expose press haptic methods`() {
        val serviceClass = SaraikiKeyboardService::class.java
        val triggerMethod = serviceClass.getDeclaredMethod(
            "performKeyHapticFeedback",
            KeyPressHapticType::class.java
        )
        assertEquals(Boolean::class.javaPrimitiveType, triggerMethod.returnType)

        val enabledGetter = serviceClass.getDeclaredMethod("isHapticFeedbackEnabled")
        assertEquals(Boolean::class.javaPrimitiveType, enabledGetter.returnType)

        val durationGetter = serviceClass.getDeclaredMethod("getHapticFeedbackDurationMs")
        assertEquals(Int::class.javaPrimitiveType, durationGetter.returnType)

        val onKeyPressedMethod = KeyboardInteractionListener::class.java.getDeclaredMethod(
            "onKeyPressed",
            KeyboardKey::class.java
        )
        assertEquals(Void.TYPE, onKeyPressedMethod.returnType)
    }
}
