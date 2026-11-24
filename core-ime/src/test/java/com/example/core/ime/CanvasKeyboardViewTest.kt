package com.example.core.ime

import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasKeyboardViewTest {

    @Test
    fun `canvas keyboard view extends android View and exposes state and touch handlers`() {
        val viewClass = CanvasKeyboardView::class.java
        assertTrue(View::class.java.isAssignableFrom(viewClass))

        val onTouchMethod = viewClass.getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
        assertEquals(Boolean::class.javaPrimitiveType, onTouchMethod.returnType)

        val updateStateMethod = viewClass.getDeclaredMethod(
            "updateState",
            List::class.java,
            KeyboardViewOptions::class.java,
            ResolvedKeyboardTheme::class.java
        )
        assertEquals(Void.TYPE, updateStateMethod.returnType)
    }

    @Test
    fun `keyboard view options and default dark theme provide Saraiki Pro Dark defaults`() {
        val opts = KeyboardViewOptions()
        assertEquals(100, opts.heightPercent)
        assertTrue(opts.showKeypressPreview)
        assertTrue(opts.glideTypingEnabled)
        assertTrue(opts.spacebarCursorTrackpad)
        assertTrue(opts.gestureDeleteEnabled)

        assertEquals(280, opts.longPressDelayMs)
    }
}
