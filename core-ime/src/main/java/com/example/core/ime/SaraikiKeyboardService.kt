package com.example.core.ime

import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.core.database.LearnedWordEntity
import com.example.core.database.SaraikiKeyboardDatabase
import com.example.core.database.WordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Core Input Method Service entry point for Saraiki Pro Keyboard located in `:core-ime`.
 * Handles Android IME lifecycle events, visibility tracking, language toggling across
 * Saraiki, Urdu, and English via the globe key, and host container initialization for
 * custom Canvas keyboard rendering.
 */
open class SaraikiKeyboardService : InputMethodService() {

    private var engineDelegate: ImeEngineDelegate? = null
    private var attachedWordViewModel: KeyboardViewModel? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wordRepository: WordRepository by lazy { createWordRepository() }
    private val hapticFeedback: KeyboardHapticFeedback by lazy { KeyboardHapticFeedback(this) }
    private var pressHapticTriggeredForCurrentKey: Boolean = false
    private val composingWord = StringBuilder()
    private var lastCommittedWord: String? = null

    var isHapticFeedbackEnabled: Boolean = true
    var hapticFeedbackDurationMs: Int = KeyboardHapticFeedback.DEFAULT_DURATION_MS

    protected var rootInputContainer: FrameLayout? = null
        private set

    protected var candidateView: CandidateView? = null
        private set

    protected var defaultKeyboardView: CanvasKeyboardView? = null
        private set

    private val _activeLanguage = MutableStateFlow(ImeLanguage.SARAIKI)
    val activeLanguageFlow: StateFlow<ImeLanguage> = _activeLanguage.asStateFlow()

    val activeLanguage: ImeLanguage
        get() = _activeLanguage.value

    var currentPage: CoreKeyboardPage = CoreKeyboardPage.LETTERS
        private set

    var isShifted: Boolean = false
        private set

    var currentRows: List<List<KeyboardKey>> = CoreKeyboardLayouts.buildRows(ImeLanguage.SARAIKI)
        private set

    var isKeyboardVisible: Boolean = false
        private set

    var isInputSessionActive: Boolean = false
        private set

    var currentImeOptions: Int = EditorInfo.IME_ACTION_NONE
        private set

    private val defaultInteractionListener = object : KeyboardInteractionListener {
        override fun onKeyPressed(key: KeyboardKey) {
            val type = KeyboardHapticFeedback.resolveHapticType(key.action)
            pressHapticTriggeredForCurrentKey = if (type != null) {
                performKeyHapticFeedback(type)
                true
            } else {
                false
            }
        }

        override fun onKeyAction(action: KeyAction) {
            handleDefaultKeyAction(action)
        }

        override fun onPopupItemCommitted(text: String) {
            pressHapticTriggeredForCurrentKey = false
            performKeyHapticFeedback(KeyPressHapticType.REGULAR)
            currentInputConnection?.commitText(text, 1)
        }

        override fun onGlideCompleted(points: List<GlidePoint>, keyCenters: List<KeyCenter>) {
            pressHapticTriggeredForCurrentKey = false
        }

        override fun onSpacebarCursorDelta(steps: Int) {
            val ic = currentInputConnection ?: return
            if (steps == 0) return
            val keyCode = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            repeat(kotlin.math.abs(steps)) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }

        override fun onSpacebarLongPress() {
            pressHapticTriggeredForCurrentKey = false
            performKeyHapticFeedback(KeyPressHapticType.SPACE_OR_ENTER)
            toggleNextLanguage()
        }

        override fun onGestureDeleteWord() {
            pressHapticTriggeredForCurrentKey = false
            performKeyHapticFeedback(KeyPressHapticType.BACKSPACE_REPEAT)
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        override fun onBackspaceRepeatStep(deleteWholeWord: Boolean) {
            pressHapticTriggeredForCurrentKey = false
            performKeyHapticFeedback(KeyPressHapticType.BACKSPACE_REPEAT)
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        override fun onPopupFocusTick() {
            performKeyHapticFeedback(KeyPressHapticType.POPUP_TICK)
        }
    }

    override fun onCreate() {
        super.onCreate()
        engineDelegate = resolveDelegate()
        engineDelegate?.onServiceCreate(this)
    }

    override fun onInitializeInterface() {
        super.onInitializeInterface()
        if (engineDelegate == null) {
            engineDelegate = resolveDelegate()?.also { it.onServiceCreate(this) }
        }
        engineDelegate?.onInitializeInterface()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return engineDelegate?.onEvaluateFullscreenMode() ?: false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        defaultKeyboardView?.releasePopups()
        engineDelegate?.onConfigurationChanged(newConfig)
    }

    override fun onCreateInputView(): View {
        val delegate = engineDelegate ?: resolveDelegate()?.also {
            engineDelegate = it
            it.onServiceCreate(this)
            it.onInitializeInterface()
        }
        if (delegate != null) {
            return delegate.onCreateInputView(this)
        }
        return createBaseInputContainer()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        isInputSessionActive = true
        currentImeOptions = attribute?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        composingWord.clear()
        candidateView?.updateQuery("", activeLanguage.code)
        refreshDefaultLayoutRows()
        engineDelegate?.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputSessionActive = true
        isKeyboardVisible = true
        currentImeOptions = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        refreshDefaultLayoutRows()
        engineDelegate?.onStartInputView(info, restarting)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        isKeyboardVisible = true
        engineDelegate?.onWindowShown()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        isKeyboardVisible = false
        defaultKeyboardView?.releasePopups()
        engineDelegate?.onWindowHidden()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isKeyboardVisible = false
        defaultKeyboardView?.releasePopups()
        engineDelegate?.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        isInputSessionActive = false
        isKeyboardVisible = false
        composingWord.clear()
        engineDelegate?.onFinishInput()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        engineDelegate?.onComputeInsets(outInsets)
    }

    override fun onDestroy() {
        candidateView?.release()
        candidateView = null
        defaultKeyboardView?.releasePopups()
        defaultKeyboardView = null
        engineDelegate?.onDestroy()
        engineDelegate = null
        attachedWordViewModel = null
        rootInputContainer = null
        isKeyboardVisible = false
        isInputSessionActive = false
        serviceScope.cancel()
        super.onDestroy()
    }

    fun attachWordViewModel(viewModel: KeyboardViewModel?) {
        attachedWordViewModel = viewModel
        viewModel?.setLanguage(_activeLanguage.value.code)
    }

    /**
     * Cycles to the next supported keyboard layout (Saraiki -> Urdu -> English -> Saraiki).
     */
    fun toggleNextLanguage(): ImeLanguage {
        return switchLanguage(_activeLanguage.value.next())
    }

    /**
     * Switches the active layout to [language], resets temporary shift/symbol state,
     * and updates the rendered [CanvasKeyboardView] rows.
     */
    fun switchLanguage(language: ImeLanguage): ImeLanguage {
        commitActiveComposingWord(appendSpace = false)
        _activeLanguage.value = language
        currentPage = CoreKeyboardPage.LETTERS
        isShifted = false
        lastCommittedWord = null
        attachedWordViewModel?.setLanguage(language.code)
        candidateView?.updateQuery("", language.code)
        refreshDefaultLayoutRows()
        return language
    }

    /**
     * Keeps [SaraikiKeyboardService] language state synchronized when an external
     * [ImeEngineDelegate] manages persisted language settings.
     */
    fun syncLanguageFromHost(language: ImeLanguage) {
        if (_activeLanguage.value == language) return
        _activeLanguage.value = language
        currentPage = CoreKeyboardPage.LETTERS
        isShifted = false
        attachedWordViewModel?.setLanguage(language.code)
        candidateView?.updateQuery("", language.code)
        currentRows = CoreKeyboardLayouts.buildRows(
            language = language,
            page = currentPage,
            shifted = isShifted,
            imeOptions = currentImeOptions
        )
    }

    fun performKeyHapticFeedback(type: KeyPressHapticType = KeyPressHapticType.REGULAR): Boolean {
        return hapticFeedback.vibrate(
            type = type,
            baseDurationMs = hapticFeedbackDurationMs,
            enabled = isHapticFeedbackEnabled,
            fallbackView = defaultKeyboardView
        )
    }

    protected open fun handleDefaultKeyAction(action: KeyAction) {
        val alreadyVibratedOnPress = pressHapticTriggeredForCurrentKey
        pressHapticTriggeredForCurrentKey = false
        if (!alreadyVibratedOnPress) {
            KeyboardHapticFeedback.resolveHapticType(action)?.let { performKeyHapticFeedback(it) }
        }

        when (action) {
            KeyAction.GlobeSwitch -> {
                toggleNextLanguage()
            }
            KeyAction.Shift -> {
                isShifted = !isShifted
                refreshDefaultLayoutRows()
            }
            KeyAction.ToggleSymbols, KeyAction.ToggleMoreSymbols -> {
                commitActiveComposingWord(appendSpace = false)
                currentPage = if (currentPage == CoreKeyboardPage.LETTERS) {
                    CoreKeyboardPage.SYMBOLS
                } else {
                    CoreKeyboardPage.LETTERS
                }
                refreshDefaultLayoutRows()
            }
            is KeyAction.CommitChar -> {
                if (currentPage == CoreKeyboardPage.LETTERS && action.char.length == 1 && action.char[0].isLetter()) {
                    composingWord.append(action.char)
                    currentInputConnection?.setComposingText(composingWord.toString(), 1)
                    updateCandidatePredictions()
                } else {
                    commitActiveComposingWord(appendSpace = false)
                    currentInputConnection?.commitText(action.char, 1)
                }
                if (isShifted) {
                    isShifted = false
                    refreshDefaultLayoutRows()
                }
            }
            KeyAction.Backspace -> {
                if (composingWord.isNotEmpty()) {
                    composingWord.deleteCharAt(composingWord.length - 1)
                    if (composingWord.isEmpty()) {
                        currentInputConnection?.commitText("", 1)
                    } else {
                        currentInputConnection?.setComposingText(composingWord.toString(), 1)
                    }
                    updateCandidatePredictions()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }
            KeyAction.Space -> {
                if (composingWord.isNotEmpty()) {
                    commitActiveComposingWord(appendSpace = true)
                } else {
                    currentInputConnection?.commitText(" ", 1)
                }
            }
            KeyAction.Comma -> {
                commitActiveComposingWord(appendSpace = false)
                val comma = if (activeLanguage.isRtl) "،" else ","
                currentInputConnection?.commitText(comma, 1)
            }
            KeyAction.Period -> {
                commitActiveComposingWord(appendSpace = false)
                val period = if (activeLanguage.isRtl) "۔" else "."
                currentInputConnection?.commitText(period, 1)
            }
            KeyAction.Enter -> {
                commitActiveComposingWord(appendSpace = false)
                lastCommittedWord = null
                val ic = currentInputConnection ?: return
                val actionId = currentImeOptions and EditorInfo.IME_MASK_ACTION
                val hasNoAction = (currentImeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                if (!hasNoAction &&
                    actionId != EditorInfo.IME_ACTION_NONE &&
                    actionId != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
            }
            KeyAction.EmojiDrawer, KeyAction.Spacer -> Unit
        }
    }

    protected open fun createWordRepository(): WordRepository =
        WordRepository.fromDatabase(SaraikiKeyboardDatabase.getInstance(applicationContext))

    protected open fun onCandidateChosen(candidate: LearnedWordEntity) {
        performKeyHapticFeedback(KeyPressHapticType.REGULAR)
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText("${candidate.word} ", 1)
        }
        val previous = lastCommittedWord
        composingWord.clear()
        lastCommittedWord = candidate.word
        attachedWordViewModel?.selectSuggestion(candidate) ?: recordWordAsync(
            word = candidate.word,
            languageCode = activeLanguage.code,
            isUserCoined = candidate.isUserCoined,
            previousWord = previous
        )
        updateCandidatePredictions()
    }

    private fun commitActiveComposingWord(appendSpace: Boolean) {
        if (composingWord.isEmpty()) return
        val committed = composingWord.toString().trim()
        composingWord.clear()
        val ic = currentInputConnection
        if (ic != null) {
            if (appendSpace) {
                ic.commitText("$committed ", 1)
            } else {
                ic.finishComposingText()
            }
        }
        if (committed.isNotEmpty()) {
            val previous = lastCommittedWord
            lastCommittedWord = committed
            attachedWordViewModel?.commitTypedWord(committed, isUserCoined = true) ?: recordWordAsync(
                word = committed,
                languageCode = activeLanguage.code,
                isUserCoined = true,
                previousWord = previous
            )
        }
        updateCandidatePredictions()
    }

    private fun recordWordAsync(
        word: String,
        languageCode: String,
        isUserCoined: Boolean,
        previousWord: String?
    ) {
        serviceScope.launch {
            wordRepository.insertOrIncrementWord(
                word = word,
                language = languageCode,
                isUserCoined = isUserCoined,
                previousWord = previousWord
            )
        }
    }

    private fun updateCandidatePredictions() {
        val prefix = composingWord.toString()
        attachedWordViewModel?.onComposingPrefixChanged(prefix)
        candidateView?.updateQuery(prefix, activeLanguage.code)
    }

    private fun refreshDefaultLayoutRows() {
        val rows = CoreKeyboardLayouts.buildRows(
            language = _activeLanguage.value,
            page = currentPage,
            shifted = isShifted,
            imeOptions = currentImeOptions
        )
        currentRows = rows
        defaultKeyboardView?.updateState(rows)
    }

    protected open fun createBaseInputContainer(): View {
        val strip = CandidateView(this).apply {
            bindWordRepository(
                repository = wordRepository,
                language = activeLanguage.code
            )
            onCandidateSelected = { candidate -> onCandidateChosen(candidate) }
            onCandidateLongPressed = { candidate ->
                serviceScope.launch {
                    wordRepository.deleteWord(candidate.language, candidate.word)
                }
            }
        }.also { candidateView = it }

        val keyboardView = CanvasKeyboardView(this).apply {
            listener = defaultInteractionListener
            updateState(currentRows)
        }.also { defaultKeyboardView = it }

        val verticalHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                strip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                keyboardView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        return FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#1E222B"))
            addView(
                verticalHost,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }.also { rootInputContainer = it }
    }

    private fun resolveDelegate(): ImeEngineDelegate? {
        val host = (applicationContext as? ImeHostApplication)
            ?: (application as? ImeHostApplication)
        return host?.createImeEngineDelegate()
    }
}
