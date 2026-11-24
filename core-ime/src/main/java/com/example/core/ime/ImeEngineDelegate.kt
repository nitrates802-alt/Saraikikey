package com.example.core.ime

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * Engine delegate contract implemented by the application host to supply the
 * full Canvas keyboard view, linguistic suggestions, drawers, and voice typing
 * to [SaraikiKeyboardService] in `:core-ime`.
 */
interface ImeEngineDelegate {
    fun onServiceCreate(service: InputMethodService)
    fun onInitializeInterface()
    fun onEvaluateFullscreenMode(): Boolean = false
    fun onConfigurationChanged(newConfig: Configuration)
    fun onCreateInputView(service: InputMethodService): View
    fun onStartInput(attribute: EditorInfo?, restarting: Boolean)
    fun onStartInputView(info: EditorInfo?, restarting: Boolean)
    fun onWindowShown()
    fun onWindowHidden()
    fun onFinishInputView(finishingInput: Boolean)
    fun onFinishInput()
    fun onComputeInsets(outInsets: InputMethodService.Insets)
    fun onDestroy()
}

/**
 * Implemented by the Android [android.app.Application] to provide an [ImeEngineDelegate]
 * instance to [SaraikiKeyboardService].
 */
interface ImeHostApplication {
    fun createImeEngineDelegate(): ImeEngineDelegate
}
