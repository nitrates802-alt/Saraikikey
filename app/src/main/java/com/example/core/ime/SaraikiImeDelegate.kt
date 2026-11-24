package com.example.core.ime

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.AppContainer
import com.example.MainActivity
import com.example.R
import com.example.core.database.ClipboardEntryEntity
import com.example.core.linguistics.GlidePoint
import com.example.core.linguistics.KeyCenter
import com.example.core.linguistics.SaraikiCharacterConfig
import com.example.core.linguistics.SuggestionCandidate
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardLanguage
import com.example.core.preferences.KeyboardSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SaraikiImeDelegate(
    private val appContainer: AppContainer
) : ImeEngineDelegate,
    KeyboardInteractionListener,
    SuggestionStripListener {

    private enum class ActiveDrawer { NONE, AI_ASSISTANT, EMOJI, CLIPBOARD, EDIT_PAD, VOICE, RESIZER }

    private lateinit var service: InputMethodService
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val settingsRepo by lazy { appContainer.settingsRepository }
    private val keyboardRepo by lazy { appContainer.keyboardRepository }
    private val linguisticEngine by lazy { appContainer.linguisticEngine }
    private val soundHapticEngine by lazy { appContainer.soundHapticEngine }
    private val aiRepository by lazy { appContainer.aiRepository }

    private var currentSettings: KeyboardSettings = KeyboardSettings()
    private var currentTheme: ResolvedKeyboardTheme? = null
    private var shiftState: ShiftState = ShiftState.OFF
    private var currentPage: KeyboardPage = KeyboardPage.LETTERS
    private var activeDrawer: ActiveDrawer = ActiveDrawer.NONE
    private var currentImeOptions: Int = EditorInfo.IME_ACTION_NONE
    private var isIncognitoField: Boolean = false

    // Typing state
    private val composingBuffer = StringBuilder()
    private var prevWord: String? = null
    private var prev2Word: String? = null
    private var lastSpaceTapTimeMs: Long = 0L
    private var justCommittedByBoundary: Boolean = false
    private var lastGestureDeletedText: String? = null
    private var currentCandidates: List<SuggestionCandidate> = emptyList()
    private var cachedClipboard: List<ClipboardEntryEntity> = emptyList()

    // Views
    private var outerHost: FrameLayout? = null
    private var keyboardCard: LinearLayout? = null
    private var floatingHandleBar: LinearLayout? = null
    private var floatingAlphaBtn: TextView? = null
    private var suggestionStrip: SuggestionStripView? = null
    private var mainBodyRow: LinearLayout? = null
    private var oneHandedRail: LinearLayout? = null
    private var canvasKeyboardView: CanvasKeyboardView? = null
    private var drawerHost: FrameLayout? = null

    private var aiDrawer: AiAssistantDrawerView? = null
    private var emojiDrawer: EmojiMediaDrawerView? = null
    private var clipboardDrawer: ClipboardDrawerView? = null
    private var editPadDrawer: TextEditPadView? = null
    private var voiceOverlay: VoiceTypingOverlayView? = null
    private var resizerOverlay: LiveResizerOverlayView? = null

    private var clipboardManager: ClipboardManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var observeJob: Job? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val settings = currentSettings
        if (!settings.clipboardEnabled || isIncognitoField) return@OnPrimaryClipChangedListener
        val clip = clipboardManager?.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(service)?.toString().orEmpty()
            if (text.isNotBlank()) {
                serviceScope.launch {
                    keyboardRepo.addClipboardSnippet(text, settings.clipboardRetentionLimit)
                }
            }
        }
    }

    override fun onServiceCreate(service: InputMethodService) {
        this.service = service
        clipboardManager = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        soundHapticEngine.initializeAsync()

        observeJob = serviceScope.launch {
            launch {
                settingsRepo.settingsFlow.collectLatest { settings ->
                    currentSettings = settings
                    applySettingsToUi(settings)
                }
            }
            launch {
                keyboardRepo.clipboardItems.collectLatest { items ->
                    cachedClipboard = items
                    clipboardDrawer?.render(items, isIncognitoField)
                }
            }
        }
    }

    override fun onInitializeInterface() {
        currentSettings = settingsRepo.settingsFlow.value
        currentTheme = KeyboardThemeTokens.resolve(service, currentSettings)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigurationChanged(newConfig: Configuration) {
        canvasKeyboardView?.releasePopups()
        applySettingsToUi(currentSettings)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(service: InputMethodService): View {
        this.service = service
        val host = FrameLayout(service).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Floating drag handle (visible only in FLOATING mode)
        val handleBar = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            visibility = View.GONE
        }
        val dragTitle = TextView(service).apply {
            text = "⠿ Drag Floating Keyboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val alphaBtn = TextView(service).apply {
            text = "◐"
            setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
            setOnClickListener {
                val nextAlpha = if (currentSettings.floatingAlpha > 0.8f) 0.65f else 0.96f
                serviceScope.launch {
                    settingsRepo.updateSettings { it.copy(floatingAlpha = nextAlpha) }
                }
            }
        }
        val dockBtn = TextView(service).apply {
            text = "⤢ Dock"
            setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
            setOnClickListener {
                serviceScope.launch {
                    settingsRepo.updateSettings { it.copy(ergonomicMode = ErgonomicMode.NORMAL) }
                }
            }
        }
        handleBar.addView(dragTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        handleBar.addView(alphaBtn)
        handleBar.addView(dockBtn)

        var dragStartRawX = 0f
        var startMargin = 0
        handleBar.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = ev.rawX
                    startMargin = currentSettings.floatingX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dragStartRawX).roundToInt()
                    val newX = (startMargin + dx).coerceIn(0, (service.resources.displayMetrics.widthPixels * 0.25f).roundToInt())
                    (keyboardCard?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                        lp.leftMargin = newX
                        keyboardCard?.layoutParams = lp
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = (ev.rawX - dragStartRawX).roundToInt()
                    val newX = (startMargin + dx).coerceIn(0, (service.resources.displayMetrics.widthPixels * 0.25f).roundToInt())
                    serviceScope.launch {
                        settingsRepo.updateSettings { it.copy(floatingX = newX) }
                    }
                    true
                }
                else -> false
            }
        }

        val strip = SuggestionStripView(service).apply {
            listener = this@SaraikiImeDelegate
        }

        val bodyRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val rail = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(4f), dp(8f), dp(4f), dp(8f))
        }
        val expandBtn = TextView(service).apply {
            text = "⤢"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setOnClickListener {
                serviceScope.launch {
                    settingsRepo.updateSettings { it.copy(ergonomicMode = ErgonomicMode.NORMAL) }
                }
            }
        }
        val flipBtn = TextView(service).apply {
            text = "⇄"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setOnClickListener {
                serviceScope.launch {
                    settingsRepo.updateSettings { s ->
                        val flipped = if (s.ergonomicMode == ErgonomicMode.ONE_HANDED_LEFT) {
                            ErgonomicMode.ONE_HANDED_RIGHT
                        } else {
                            ErgonomicMode.ONE_HANDED_LEFT
                        }
                        s.copy(ergonomicMode = flipped)
                    }
                }
            }
        }
        rail.addView(expandBtn, LinearLayout.LayoutParams(dp(40f), dp(48f)))
        rail.addView(flipBtn, LinearLayout.LayoutParams(dp(40f), dp(48f)).apply { topMargin = dp(12f) })

        val canvasView = CanvasKeyboardView(service).apply {
            listener = this@SaraikiImeDelegate
            setTypefaces(
                arabic = androidx.core.content.res.ResourcesCompat.getFont(service, R.font.noto_naskh_arabic),
                latin = androidx.core.content.res.ResourcesCompat.getFont(service, R.font.plus_jakarta_sans)
            )
        }

        val drawer = FrameLayout(service).apply {
            visibility = View.GONE
        }

        bodyRow.addView(canvasView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(handleBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46f)))
        card.addView(bodyRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(drawer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        host.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        outerHost = host
        keyboardCard = card
        floatingHandleBar = handleBar
        floatingAlphaBtn = alphaBtn
        suggestionStrip = strip
        mainBodyRow = bodyRow
        oneHandedRail = rail
        canvasKeyboardView = canvasView
        drawerHost = drawer

        currentSettings = settingsRepo.settingsFlow.value
        applySettingsToUi(currentSettings)
        return host
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        currentImeOptions = attribute?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        isIncognitoField = isSensitiveOrIncognito(attribute)
        composingBuffer.clear()
        if (!restarting) {
            prevWord = null
            prev2Word = null
            justCommittedByBoundary = false
            lastGestureDeletedText = null
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        currentImeOptions = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        isIncognitoField = isSensitiveOrIncognito(info)

        composingBuffer.clear()
        prevWord = null
        prev2Word = null
        justCommittedByBoundary = false
        lastGestureDeletedText = null
        activeDrawer = ActiveDrawer.NONE
        updateAutoCapShiftState(info)
        applySettingsToUi(currentSettings)
        refreshSuggestions()
    }

    override fun onWindowShown() {
        applySettingsToUi(currentSettings)
        refreshSuggestions()
    }

    override fun onWindowHidden() {
        canvasKeyboardView?.releasePopups()
        stopVoiceListening()
        linguisticEngine.flushPendingToken()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        canvasKeyboardView?.releasePopups()
        stopVoiceListening()
        linguisticEngine.flushPendingToken()
    }

    override fun onFinishInput() {
        composingBuffer.clear()
        linguisticEngine.flushPendingToken()
        activeDrawer = ActiveDrawer.NONE
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        val host = outerHost ?: return
        val card = keyboardCard ?: return
        val totalHeight = host.height
        if (totalHeight > 0 && card.height > 0) {
            val top = (totalHeight - card.height).coerceAtLeast(0)
            outInsets.contentTopInsets = top
            outInsets.visibleTopInsets = top
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
        }
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        canvasKeyboardView?.releasePopups()
        stopVoiceListening()
    }

    private fun isSensitiveOrIncognito(info: EditorInfo?): Boolean {
        if (info == null) return false
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun applySettingsToUi(settings: KeyboardSettings) {
        val resolved = KeyboardThemeTokens.resolve(service, settings)
        currentTheme = resolved

        val screenW = service.resources.displayMetrics.widthPixels
        val card = keyboardCard ?: return
        val cardLp = (card.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        when (settings.ergonomicMode) {
            ErgonomicMode.ONE_HANDED_LEFT -> {
                val targetW = (screenW * 0.84f).roundToInt()
                cardLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                cardLp.gravity = Gravity.BOTTOM
                cardLp.leftMargin = 0
                card.alpha = 1f
                floatingHandleBar?.visibility = View.GONE
                configureOneHandedRail(leftAnchored = true, totalW = targetW)
            }
            ErgonomicMode.ONE_HANDED_RIGHT -> {
                val targetW = (screenW * 0.84f).roundToInt()
                cardLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                cardLp.gravity = Gravity.BOTTOM
                cardLp.leftMargin = 0
                card.alpha = 1f
                floatingHandleBar?.visibility = View.GONE
                configureOneHandedRail(leftAnchored = false, totalW = targetW)
            }
            ErgonomicMode.FLOATING -> {
                val targetW = (screenW * 0.86f).roundToInt()
                cardLp.width = targetW
                cardLp.leftMargin = settings.floatingX.coerceIn(0, (screenW - targetW).coerceAtLeast(0))
                cardLp.gravity = Gravity.BOTTOM
                card.alpha = settings.floatingAlpha
                floatingHandleBar?.visibility = View.VISIBLE
                floatingHandleBar?.setBackgroundColor(resolved.stripBackgroundColor)
                hideOneHandedRail()
            }
            ErgonomicMode.NORMAL, ErgonomicMode.SPLIT_TABLET -> {
                val widthFrac = (settings.widthPercent / 100f).coerceIn(0.7f, 1f)
                cardLp.width = if (widthFrac >= 0.99f) ViewGroup.LayoutParams.MATCH_PARENT else (screenW * widthFrac).roundToInt()
                cardLp.leftMargin = 0
                cardLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                card.alpha = 1f
                floatingHandleBar?.visibility = View.GONE
                hideOneHandedRail()
            }
        }
        card.layoutParams = cardLp
        card.setBackgroundColor(resolved.backgroundColor)

        (service as? SaraikiKeyboardService)?.apply {
            syncLanguageFromHost(ImeLanguage.fromCode(settings.activeLanguage.code))
            isHapticFeedbackEnabled = settings.hapticEnabled
            hapticFeedbackDurationMs = settings.hapticDurationMs
        }
        val rows = KeyboardLayoutDefinitions.buildRows(
            language = settings.activeLanguage,
            page = currentPage,
            shiftState = shiftState,
            showNumberRow = settings.showNumberRow,
            ergonomicMode = settings.ergonomicMode,
            imeOptions = currentImeOptions,
            saraikiConfig = appContainer.saraikiConfig
        )
        canvasKeyboardView?.updateState(rows, settings, resolved)
        suggestionStrip?.applyTheme(resolved)
        aiDrawer?.applyTheme(resolved)
        emojiDrawer?.applyTheme(resolved)
        clipboardDrawer?.applyTheme(resolved)
        editPadDrawer?.applyTheme(resolved)
        voiceOverlay?.applyTheme(resolved)
        resizerOverlay?.bind(settings, resolved)
        updateDrawerVisibility()
    }

    private fun configureOneHandedRail(leftAnchored: Boolean, totalW: Int) {
        val body = mainBodyRow ?: return
        val rail = oneHandedRail ?: return
        val canvas = canvasKeyboardView ?: return
        body.removeAllViews()
        rail.visibility = View.VISIBLE
        rail.setBackgroundColor(currentTheme?.stripBackgroundColor ?: Color.DKGRAY)
        for (i in 0 until rail.childCount) {
            (rail.getChildAt(i) as? TextView)?.setTextColor(currentTheme?.primaryTextColor ?: Color.WHITE)
        }
        if (leftAnchored) {
            body.addView(canvas, LinearLayout.LayoutParams(totalW, ViewGroup.LayoutParams.WRAP_CONTENT))
            body.addView(rail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            body.addView(rail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            body.addView(canvas, LinearLayout.LayoutParams(totalW, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun hideOneHandedRail() {
        val body = mainBodyRow ?: return
        val canvas = canvasKeyboardView ?: return
        if (oneHandedRail?.visibility != View.GONE || body.childCount != 1) {
            oneHandedRail?.visibility = View.GONE
            body.removeAllViews()
            body.addView(canvas, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun updateDrawerVisibility() {
        val host = drawerHost ?: return
        val body = mainBodyRow ?: return
        if (activeDrawer == ActiveDrawer.NONE) {
            host.visibility = View.GONE
            body.visibility = View.VISIBLE
            return
        }
        body.visibility = View.GONE
        host.visibility = View.VISIBLE
        host.removeAllViews()

        val th = currentTheme ?: KeyboardThemeTokens.resolve(service, currentSettings)
        val view: View = when (activeDrawer) {
            ActiveDrawer.AI_ASSISTANT -> {
                val seed = composingBuffer.toString().ifBlank {
                    service.currentInputConnection?.getTextBeforeCursor(160, 0)?.toString().orEmpty()
                }
                (aiDrawer ?: AiAssistantDrawerView(
                    context = service,
                    aiRepository = aiRepository,
                    onInsertText = { txt ->
                        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                        service.currentInputConnection?.let { commitComposingBuffer(it, withSpace = false) }
                        service.currentInputConnection?.commitText("$txt ", 1)
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    },
                    onCloseDrawer = {
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    }
                ).also { aiDrawer = it }).apply {
                    applyTheme(th)
                    prepare(seed, currentSettings.activeLanguage)
                }
            }
            ActiveDrawer.EMOJI -> {
                (emojiDrawer ?: EmojiMediaDrawerView(
                    context = service,
                    onCommitText = { txt ->
                        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                        service.currentInputConnection?.commitText(txt, 1)
                    },
                    onBackspace = { handleBackspace() },
                    onCloseDrawer = {
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    }
                ).also { emojiDrawer = it }).apply { applyTheme(th) }
            }
            ActiveDrawer.CLIPBOARD -> {
                (clipboardDrawer ?: ClipboardDrawerView(
                    context = service,
                    onPasteText = { txt ->
                        service.currentInputConnection?.commitText(txt, 1)
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    },
                    onTogglePin = { id, pin ->
                        serviceScope.launch { keyboardRepo.toggleClipboardPin(id, pin) }
                    },
                    onDeleteItem = { id ->
                        serviceScope.launch { keyboardRepo.deleteClipboardItem(id) }
                    },
                    onClearUnpinned = {
                        serviceScope.launch { keyboardRepo.clearUnpinnedClipboard() }
                    },
                    onClose = {
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    }
                ).also { clipboardDrawer = it }).apply {
                    applyTheme(th)
                    render(cachedClipboard, isIncognitoField)
                }
            }
            ActiveDrawer.EDIT_PAD -> {
                (editPadDrawer ?: TextEditPadView(
                    context = service,
                    onDpadMove = { dx, dy, selecting -> sendDpadMove(dx, dy, selecting) },
                    onWordJump = { left, selecting -> sendWordJump(left, selecting) },
                    onHomeEnd = { home, selecting -> sendHomeEnd(home, selecting) },
                    onEditCommand = { cmd -> handleEditCommand(cmd) },
                    onClose = {
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    }
                ).also { editPadDrawer = it }).apply { applyTheme(th) }
            }
            ActiveDrawer.VOICE -> {
                (voiceOverlay ?: VoiceTypingOverlayView(
                    context = service,
                    onCancel = {
                        stopVoiceListening()
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    }
                ).also { voiceOverlay = it }).apply { applyTheme(th) }
            }
            ActiveDrawer.RESIZER -> {
                (resizerOverlay ?: LiveResizerOverlayView(
                    context = service,
                    onSizeChanged = { h, w ->
                        serviceScope.launch {
                            settingsRepo.updateSettings { it.copy(heightPercent = h, widthPercent = w) }
                        }
                    },
                    onErgonomicChanged = { mode ->
                        serviceScope.launch {
                            settingsRepo.updateSettings { it.copy(ergonomicMode = mode) }
                        }
                    },
                    onThemeSelected = { preset ->
                        serviceScope.launch {
                            settingsRepo.updateSettings { it.copy(themePreset = preset) }
                        }
                    },
                    onClose = {
                        activeDrawer = ActiveDrawer.NONE
                        updateDrawerVisibility()
                    }
                ).also { resizerOverlay = it }).apply { bind(currentSettings, th) }
            }
            ActiveDrawer.NONE -> return
        }
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    // --- KeyboardInteractionListener ---

    override fun onKeyAction(action: KeyAction) {
        lastGestureDeletedText = null
        if (action == KeyAction.GlobeSwitch) {
            soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.SPACE_OR_ENTER)
            service.currentInputConnection?.let { commitComposingBuffer(it, withSpace = false) }
            currentPage = KeyboardPage.LETTERS
            shiftState = ShiftState.OFF
            serviceScope.launch {
                val next = settingsRepo.cycleNextLanguage()
                (service as? SaraikiKeyboardService)?.syncLanguageFromHost(
                    ImeLanguage.fromCode(next.code)
                )
            }
            return
        }
        val ic = service.currentInputConnection ?: return

        when (action) {
            is KeyAction.CommitChar -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                val safeChar = SaraikiCharacterConfig.ensureWesternArabicDigits(action.char)
                linguisticEngine.flushPendingToken()
                justCommittedByBoundary = false

                if (safeChar.length == 1 && safeChar[0].isLetter() && currentPage == KeyboardPage.LETTERS) {
                    composingBuffer.append(safeChar)
                    ic.setComposingText(composingBuffer.toString(), 1)
                    if (shiftState == ShiftState.SHIFTED) {
                        shiftState = ShiftState.OFF
                        applySettingsToUi(currentSettings)
                    }
                } else {
                    commitComposingBuffer(ic, withSpace = false)
                    ic.commitText(safeChar, 1)
                }
                refreshSuggestions()
            }

            KeyAction.Shift -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.SHIFTED
                    ShiftState.SHIFTED -> if (currentSettings.activeLanguage == KeyboardLanguage.ENGLISH) ShiftState.CAPS_LOCK else ShiftState.OFF
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                applySettingsToUi(currentSettings)
            }

            KeyAction.Backspace -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                handleBackspace()
            }

            KeyAction.ToggleSymbols -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                commitComposingBuffer(ic, withSpace = false)
                currentPage = if (currentPage == KeyboardPage.LETTERS) KeyboardPage.SYMBOLS_1 else KeyboardPage.LETTERS
                applySettingsToUi(currentSettings)
            }

            KeyAction.ToggleMoreSymbols -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                currentPage = if (currentPage == KeyboardPage.SYMBOLS_1) KeyboardPage.SYMBOLS_2 else KeyboardPage.SYMBOLS_1
                applySettingsToUi(currentSettings)
            }

            KeyAction.Comma -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                val comma = if (currentSettings.activeLanguage.isRtl) "،" else ","
                commitBoundary(ic, comma)
            }

            KeyAction.Period -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                val period = if (currentSettings.activeLanguage.isRtl) "۔" else "."
                commitBoundary(ic, period)
                updateAutoCapShiftState(service.currentInputEditorInfo)
            }

            KeyAction.EmojiDrawer -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
                activeDrawer = if (activeDrawer == ActiveDrawer.EMOJI) ActiveDrawer.NONE else ActiveDrawer.EMOJI
                updateDrawerVisibility()
            }

            KeyAction.GlobeSwitch -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.SPACE_OR_ENTER)
                commitComposingBuffer(ic, withSpace = false)
                serviceScope.launch {
                    settingsRepo.cycleNextLanguage()
                }
            }

            KeyAction.Space -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.SPACE_OR_ENTER)
                val now = System.currentTimeMillis()
                if (currentSettings.doubleSpacePeriod &&
                    composingBuffer.isEmpty() &&
                    now - lastSpaceTapTimeMs in 1L..450L
                ) {
                    val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                    if (before.endsWith(" ") && !before.endsWith(". ") && !before.endsWith("۔ ")) {
                        ic.deleteSurroundingText(1, 0)
                        val punct = if (currentSettings.activeLanguage.isRtl) "۔ " else ". "
                        ic.commitText(punct, 1)
                        lastSpaceTapTimeMs = 0L
                        updateAutoCapShiftState(service.currentInputEditorInfo)
                        refreshSuggestions()
                        return
                    }
                }
                lastSpaceTapTimeMs = now

                if (composingBuffer.isNotEmpty()) {
                    val autoCandidate = currentCandidates.firstOrNull { it.isAutocorrect && !it.isEmoji }
                    val autocorrectOn = isAutocorrectEnabledForActiveLang()
                    val wordToCommit = if (autocorrectOn && autoCandidate != null) {
                        autoCandidate.text
                    } else {
                        composingBuffer.toString()
                    }
                    commitWord(ic, wordToCommit, trailingText = " ", confirmedBySuggestionTap = false)
                } else {
                    linguisticEngine.flushPendingToken()
                    ic.commitText(" ", 1)
                }
                refreshSuggestions()
            }

            KeyAction.Enter -> {
                soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.SPACE_OR_ENTER)
                commitComposingBuffer(ic, withSpace = false)
                val actionId = currentImeOptions and EditorInfo.IME_MASK_ACTION
                val hasNoEnterAction = (currentImeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                if (!hasNoEnterAction && actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                prevWord = null
                prev2Word = null
                updateAutoCapShiftState(service.currentInputEditorInfo)
                refreshSuggestions()
            }

            KeyAction.Spacer -> Unit
        }
    }

    override fun onPopupItemCommitted(text: String) {
        val ic = service.currentInputConnection ?: return
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
        val safeText = SaraikiCharacterConfig.ensureWesternArabicDigits(text)
        linguisticEngine.flushPendingToken()
        justCommittedByBoundary = false

        if (safeText.length == 1 && safeText[0].isLetter() && currentPage == KeyboardPage.LETTERS) {
            composingBuffer.append(safeText)
            ic.setComposingText(composingBuffer.toString(), 1)
        } else {
            commitComposingBuffer(ic, withSpace = false)
            ic.commitText(safeText, 1)
        }
        refreshSuggestions()
    }

    override fun onGlideCompleted(points: List<GlidePoint>, keyCenters: List<KeyCenter>) {
        val ic = service.currentInputConnection ?: return
        val decoded = linguisticEngine.decodeGlidePath(points, keyCenters, currentSettings.activeLanguage)
        val best = decoded.firstOrNull() ?: return
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
        if (composingBuffer.isNotEmpty()) {
            commitComposingBuffer(ic, withSpace = true)
        }
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before.isNotEmpty() && !before[0].isWhitespace()) {
            ic.commitText(" ", 1)
        }
        commitWord(ic, best.text, trailingText = " ", confirmedBySuggestionTap = true)
        refreshSuggestions()
    }

    override fun onSpacebarCursorDelta(steps: Int) {
        val ic = service.currentInputConnection ?: return
        commitComposingBuffer(ic, withSpace = false)
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps).coerceAtMost(8)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    override fun onSpacebarLongPress() {
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.POPUP_TICK)
        val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    override fun onGestureDeleteWord() {
        val ic = service.currentInputConnection ?: return
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.BACKSPACE_REPEAT)
        if (composingBuffer.isNotEmpty()) {
            lastGestureDeletedText = composingBuffer.toString()
            composingBuffer.clear()
            ic.setComposingText("", 1)
        } else {
            val deleted = deleteOneWordBeforeCursor(ic)
            if (deleted.isNotEmpty()) {
                lastGestureDeletedText = (lastGestureDeletedText.orEmpty()) + deleted
            }
        }
        refreshSuggestions()
    }

    override fun onBackspaceRepeatStep(deleteWholeWord: Boolean) {
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.BACKSPACE_REPEAT)
        if (deleteWholeWord && composingBuffer.isEmpty()) {
            service.currentInputConnection?.let { deleteOneWordBeforeCursor(it) }
            refreshSuggestions()
        } else {
            handleBackspace()
        }
    }

    override fun onPopupFocusTick() {
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.POPUP_TICK)
    }

    // --- SuggestionStripListener ---

    override fun onSuggestionTapped(candidate: SuggestionCandidate) {
        val ic = service.currentInputConnection ?: return
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
        if (candidate.isEmoji) {
            commitComposingBuffer(ic, withSpace = false)
            ic.commitText(candidate.text + " ", 1)
        } else {
            commitWord(ic, candidate.text, trailingText = " ", confirmedBySuggestionTap = true)
        }
        refreshSuggestions()
    }

    override fun onForgetWordRequested(candidate: SuggestionCandidate) {
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.POPUP_TICK)
        linguisticEngine.forgetLearnedWord(candidate.text, candidate.language)
        refreshSuggestions()
    }

    override fun onUndoDeleteTapped() {
        val ic = service.currentInputConnection ?: return
        val restored = lastGestureDeletedText ?: return
        lastGestureDeletedText = null
        ic.commitText(restored, 1)
        refreshSuggestions()
    }

    override fun onToolbarToolTapped(tool: ToolbarTool) {
        soundHapticEngine.playKeyFeedback(currentSettings, HapticKind.REGULAR)
        suggestionStrip?.setToolsExpanded(false)
        when (tool) {
            ToolbarTool.AI_ASSISTANT -> {
                activeDrawer = if (activeDrawer == ActiveDrawer.AI_ASSISTANT) ActiveDrawer.NONE else ActiveDrawer.AI_ASSISTANT
                updateDrawerVisibility()
            }
            ToolbarTool.CLIPBOARD -> {
                activeDrawer = if (activeDrawer == ActiveDrawer.CLIPBOARD) ActiveDrawer.NONE else ActiveDrawer.CLIPBOARD
                updateDrawerVisibility()
            }
            ToolbarTool.THEMES, ToolbarTool.RESIZE -> {
                activeDrawer = if (activeDrawer == ActiveDrawer.RESIZER) ActiveDrawer.NONE else ActiveDrawer.RESIZER
                updateDrawerVisibility()
            }
            ToolbarTool.ONE_HANDED -> {
                serviceScope.launch {
                    settingsRepo.updateSettings { s ->
                        val next = if (s.ergonomicMode == ErgonomicMode.ONE_HANDED_RIGHT) {
                            ErgonomicMode.NORMAL
                        } else {
                            ErgonomicMode.ONE_HANDED_RIGHT
                        }
                        s.copy(ergonomicMode = next)
                    }
                }
            }
            ToolbarTool.FLOATING -> {
                serviceScope.launch {
                    settingsRepo.updateSettings { s ->
                        val next = if (s.ergonomicMode == ErgonomicMode.FLOATING) {
                            ErgonomicMode.NORMAL
                        } else {
                            ErgonomicMode.FLOATING
                        }
                        s.copy(ergonomicMode = next)
                    }
                }
            }
            ToolbarTool.VOICE -> launchVoiceTyping()
            ToolbarTool.EDIT_PAD -> {
                activeDrawer = if (activeDrawer == ActiveDrawer.EDIT_PAD) ActiveDrawer.NONE else ActiveDrawer.EDIT_PAD
                updateDrawerVisibility()
            }
            ToolbarTool.SETTINGS -> {
                val intent = Intent(service, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                service.startActivity(intent)
            }
        }
    }

    // --- Core Input Logic ---

    private fun handleBackspace() {
        val ic = service.currentInputConnection ?: return
        if (justCommittedByBoundary) {
            justCommittedByBoundary = false
            linguisticEngine.onImmediateBackspaceAfterCommit()
        }

        if (composingBuffer.isNotEmpty()) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)
            ic.setComposingText(composingBuffer.toString(), 1)
        } else {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
        refreshSuggestions()
    }

    private fun deleteOneWordBeforeCursor(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        if (before.isEmpty()) return ""
        var idx = before.length
        while (idx > 0 && before[idx - 1].isWhitespace()) idx--
        while (idx > 0 && !before[idx - 1].isWhitespace()) idx--
        val deleteCount = before.length - idx
        if (deleteCount > 0) {
            val removed = before.substring(idx)
            ic.deleteSurroundingText(deleteCount, 0)
            return removed
        }
        return ""
    }

    private fun commitBoundary(ic: InputConnection, punct: String) {
        val trailing = if (currentSettings.smartPunctuation) "$punct " else punct
        if (composingBuffer.isNotEmpty()) {
            commitWord(ic, composingBuffer.toString(), trailingText = trailing, confirmedBySuggestionTap = false)
        } else {
            linguisticEngine.flushPendingToken()
            ic.commitText(trailing, 1)
        }
        refreshSuggestions()
    }

    private fun commitComposingBuffer(ic: InputConnection, withSpace: Boolean) {
        if (composingBuffer.isEmpty()) return
        val word = composingBuffer.toString()
        commitWord(ic, word, trailingText = if (withSpace) " " else "", confirmedBySuggestionTap = false)
    }

    private fun commitWord(
        ic: InputConnection,
        word: String,
        trailingText: String,
        confirmedBySuggestionTap: Boolean
    ) {
        val safeWord = SaraikiCharacterConfig.ensureWesternArabicDigits(word)
        ic.commitText(safeWord + trailingText, 1)
        composingBuffer.clear()

        linguisticEngine.onWordCommitted(
            rawWord = safeWord,
            prevWord = prevWord,
            prev2Word = prev2Word,
            activeLanguage = currentSettings.activeLanguage,
            learnEnabled = currentSettings.learnFromTypedWords,
            isIncognito = isIncognitoField,
            confirmedBySuggestionTap = confirmedBySuggestionTap
        )
        justCommittedByBoundary = !confirmedBySuggestionTap
        prev2Word = prevWord
        prevWord = safeWord
    }

    private fun refreshSuggestions() {
        val candidates = linguisticEngine.predictCandidates(
            composing = composingBuffer.toString(),
            prevWord = prevWord,
            prev2Word = prev2Word,
            activeLanguage = currentSettings.activeLanguage,
            enabledLanguages = currentSettings.enabledLanguages,
            multilingualEnabled = currentSettings.multilingualTyping,
            autocorrectEnabled = isAutocorrectEnabledForActiveLang(),
            nextWordPredictionEnabled = currentSettings.nextWordPrediction
        )
        currentCandidates = candidates
        suggestionStrip?.updateSuggestions(
            candidates = candidates,
            incognito = isIncognitoField,
            hasUndoDelete = !lastGestureDeletedText.isNullOrEmpty()
        )
    }

    private fun isAutocorrectEnabledForActiveLang(): Boolean = when (currentSettings.activeLanguage) {
        KeyboardLanguage.SARAIKI -> currentSettings.autocorrectSaraiki
        KeyboardLanguage.URDU -> currentSettings.autocorrectUrdu
        KeyboardLanguage.ENGLISH -> currentSettings.autocorrectEnglish
    }

    private fun updateAutoCapShiftState(info: EditorInfo?) {
        if (currentSettings.activeLanguage != KeyboardLanguage.ENGLISH ||
            !currentSettings.autoCapitalization ||
            shiftState == ShiftState.CAPS_LOCK
        ) {
            return
        }
        val ic = service.currentInputConnection
        val capsMode = if (info != null && ic != null && info.inputType != InputType.TYPE_NULL) {
            ic.getCursorCapsMode(info.inputType)
        } else 0
        val newShift = if (capsMode != 0) ShiftState.SHIFTED else ShiftState.OFF
        if (newShift != shiftState) {
            shiftState = newShift
            applySettingsToUi(currentSettings)
        }
    }

    // --- Text Editing Pad Helpers ---

    private fun sendDpadMove(dx: Int, dy: Int, selecting: Boolean) {
        val ic = service.currentInputConnection ?: return
        val code = when {
            dx < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            dx > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            dy < 0 -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
        val meta = if (selecting) KeyEvent.META_SHIFT_ON else 0
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun sendWordJump(left: Boolean, selecting: Boolean) {
        val ic = service.currentInputConnection ?: return
        val code = if (left) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val meta = KeyEvent.META_CTRL_ON or (if (selecting) KeyEvent.META_SHIFT_ON else 0)
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun sendHomeEnd(home: Boolean, selecting: Boolean) {
        val ic = service.currentInputConnection ?: return
        val code = if (home) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END
        val meta = if (selecting) KeyEvent.META_SHIFT_ON else 0
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun handleEditCommand(cmd: TextEditPadView.EditCmd) {
        val ic = service.currentInputConnection ?: return
        when (cmd) {
            TextEditPadView.EditCmd.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            TextEditPadView.EditCmd.CUT -> ic.performContextMenuAction(android.R.id.cut)
            TextEditPadView.EditCmd.COPY -> ic.performContextMenuAction(android.R.id.copy)
            TextEditPadView.EditCmd.PASTE -> ic.performContextMenuAction(android.R.id.paste)
        }
    }

    // --- Voice Typing ---

    private fun launchVoiceTyping() {
        val granted = ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            val intent = Intent(service, VoicePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(service)) {
            activeDrawer = ActiveDrawer.VOICE
            updateDrawerVisibility()
            voiceOverlay?.setStatus("On-device speech recognition is unavailable on this device")
            return
        }

        activeDrawer = ActiveDrawer.VOICE
        updateDrawerVisibility()
        voiceOverlay?.setStatus(service.getString(R.string.ime_voice_listening))

        stopVoiceListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(service)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                voiceOverlay?.updateRms(rmsdB)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                voiceOverlay?.setStatus("Tap Back to Keyboard or try again")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty().trim()
                if (spoken.isNotEmpty()) {
                    service.currentInputConnection?.commitText("$spoken ", 1)
                }
                activeDrawer = ActiveDrawer.NONE
                updateDrawerVisibility()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    voiceOverlay?.setStatus(partial)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val localeTag = when (currentSettings.activeLanguage) {
            KeyboardLanguage.SARAIKI, KeyboardLanguage.URDU -> "ur-PK"
            KeyboardLanguage.ENGLISH -> "en-US"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    private fun stopVoiceListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()
}
