package com.example.core.ime

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaraikiKeyboardServiceTest {

    @Test
    fun `saraiki keyboard service extends InputMethodService and declares core lifecycle methods`() {
        val serviceClass = SaraikiKeyboardService::class.java
        assertTrue(InputMethodService::class.java.isAssignableFrom(serviceClass))

        val onCreateInputView = serviceClass.getDeclaredMethod("onCreateInputView")
        assertEquals(View::class.java, onCreateInputView.returnType)

        val onStartInputView = serviceClass.getDeclaredMethod(
            "onStartInputView",
            EditorInfo::class.java,
            Boolean::class.javaPrimitiveType
        )
        assertEquals(Void.TYPE, onStartInputView.returnType)

        val onFinishInputView = serviceClass.getDeclaredMethod(
            "onFinishInputView",
            Boolean::class.javaPrimitiveType
        )
        assertEquals(Void.TYPE, onFinishInputView.returnType)
    }

    @Test
    fun `ime engine delegate contract captures lifecycle transitions`() {
        val events = mutableListOf<String>()
        val delegate = object : ImeEngineDelegate {
            override fun onServiceCreate(service: InputMethodService) {
                events += "create"
            }

            override fun onInitializeInterface() {
                events += "initInterface"
            }

            override fun onConfigurationChanged(newConfig: Configuration) {
                events += "config"
            }

            override fun onCreateInputView(service: InputMethodService): View {
                events += "createInputView"
                throw UnsupportedOperationException("Not called in JVM stub test")
            }

            override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
                events += "startInput:$restarting"
            }

            override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
                events += "startInputView:$restarting"
            }

            override fun onWindowShown() {
                events += "windowShown"
            }

            override fun onWindowHidden() {
                events += "windowHidden"
            }

            override fun onFinishInputView(finishingInput: Boolean) {
                events += "finishInputView:$finishingInput"
            }

            override fun onFinishInput() {
                events += "finishInput"
            }

            override fun onComputeInsets(outInsets: InputMethodService.Insets) {
                events += "computeInsets"
            }

            override fun onDestroy() {
                events += "destroy"
            }
        }

        assertFalse(delegate.onEvaluateFullscreenMode())
        delegate.onInitializeInterface()
        delegate.onStartInput(null, false)
        delegate.onStartInputView(null, false)
        delegate.onWindowShown()
        delegate.onFinishInputView(true)
        delegate.onFinishInput()
        delegate.onDestroy()

        assertEquals(
            listOf(
                "initInterface",
                "startInput:false",
                "startInputView:false",
                "windowShown",
                "finishInputView:true",
                "finishInput",
                "destroy"
            ),
            events
        )
    }
}
