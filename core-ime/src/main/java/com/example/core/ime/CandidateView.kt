package com.example.core.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.core.database.LearnedWordEntity
import com.example.core.database.WordRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Horizontal prediction strip rendered above [CanvasKeyboardView] inside [SaraikiKeyboardService].
 * Queries Room asynchronously via [WordRepository] as the user types to present predicted words.
 */
class CandidateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onCandidateSelected: ((LearnedWordEntity) -> Unit)? = null
    var onCandidateLongPressed: ((LearnedWordEntity) -> Unit)? = null

    private var theme: ResolvedKeyboardTheme = ResolvedKeyboardTheme.defaultDark()
    private var wordRepository: WordRepository? = null
    private var activeLanguage: String = ImeLanguage.SARAIKI.code
    private var currentPrefix: String = ""
    private var maxCandidates: Int = DEFAULT_MAX_CANDIDATES
    private var currentCandidates: List<LearnedWordEntity> = emptyList()

    private var mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    private var viewScope: CoroutineScope? = null
    private var queryJob: Job? = null

    private var customArabicTypeface: Typeface? = null
    private var customLatinTypeface: Typeface? = null

    private val arabicTypeface: Typeface by lazy {
        customArabicTypeface ?: loadBundledFont("noto_naskh_arabic") ?: Typeface.DEFAULT
    }
    private val latinTypeface: Typeface by lazy {
        customLatinTypeface ?: loadBundledFont("plus_jakarta_sans") ?: Typeface.SANS_SERIF
    }

    private val scrollView: HorizontalScrollView
    private val stripRow: LinearLayout

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48f)
        setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        setBackgroundColor(theme.stripBackgroundColor)

        stripRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(
                stripRow,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            )
        }
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, dp(40f)))
        renderCandidates()
    }

    fun bindWordRepository(
        repository: WordRepository,
        language: String = activeLanguage,
        maxResults: Int = DEFAULT_MAX_CANDIDATES,
        dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    ) {
        wordRepository = repository
        activeLanguage = language.trim().ifEmpty { ImeLanguage.SARAIKI.code }
        maxCandidates = maxResults.coerceAtLeast(1)
        mainDispatcher = dispatcher
        refreshSuggestions()
    }

    fun updateQuery(prefix: String, language: String = activeLanguage) {
        val normalizedLanguage = language.trim().ifEmpty { ImeLanguage.SARAIKI.code }
        val normalizedPrefix = prefix.trim()
        if (normalizedLanguage == activeLanguage && normalizedPrefix == currentPrefix && queryJob?.isActive == true) {
            return
        }
        activeLanguage = normalizedLanguage
        currentPrefix = normalizedPrefix
        refreshSuggestions()
    }

    fun setCandidates(candidates: List<LearnedWordEntity>) {
        currentCandidates = candidates.take(maxCandidates)
        renderCandidates()
    }

    fun getDisplayedCandidates(): List<LearnedWordEntity> = currentCandidates

    fun clearCandidates() {
        queryJob?.cancel()
        queryJob = null
        currentPrefix = ""
        currentCandidates = emptyList()
        renderCandidates()
    }

    fun applyTheme(resolvedTheme: ResolvedKeyboardTheme) {
        theme = resolvedTheme
        setBackgroundColor(resolvedTheme.stripBackgroundColor)
        renderCandidates()
    }

    fun setTypefaces(arabic: Typeface?, latin: Typeface?) {
        customArabicTypeface = arabic
        customLatinTypeface = latin
        renderCandidates()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureScope()
        if (wordRepository != null && queryJob == null) {
            refreshSuggestions()
        }
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        queryJob?.cancel()
        queryJob = null
        viewScope?.cancel()
        viewScope = null
    }

    private fun ensureScope(): CoroutineScope {
        val existing = viewScope
        if (existing != null) return existing
        return CoroutineScope(SupervisorJob() + mainDispatcher).also { viewScope = it }
    }

    private fun refreshSuggestions() {
        val repo = wordRepository ?: return
        val scope = ensureScope()
        queryJob?.cancel()

        val lang = activeLanguage
        val prefix = currentPrefix
        val limit = maxCandidates

        queryJob = scope.launch {
            val flow = if (prefix.isEmpty()) {
                repo.observeLearnedWords(lang)
            } else {
                repo.observePrefixSuggestions(lang, prefix, limit)
            }
            flow.collectLatest { matches ->
                currentCandidates = matches.take(limit)
                renderCandidates()
            }
        }
    }

    private fun renderCandidates() {
        stripRow.removeAllViews()
        if (currentCandidates.isEmpty()) {
            val placeholder = TextView(context).apply {
                text = if (currentPrefix.isEmpty()) {
                    "${ImeLanguage.fromCode(activeLanguage).nativeName} • Suggestions ready"
                } else {
                    currentPrefix
                }
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(theme.hintTextColor)
                typeface = if (placeholderContainsArabic(text.toString())) arabicTypeface else latinTypeface
                contentDescription = text
            }
            stripRow.addView(
                placeholder,
                LayoutParams(LayoutParams.MATCH_PARENT, dp(40f))
            )
            return
        }

        currentCandidates.forEachIndexed { index, entity ->
            val isPrimary = index == 0
            val isArabic = placeholderContainsArabic(entity.word)
            val pill = TextView(context).apply {
                text = entity.word
                contentDescription = entity.word
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minimumWidth = dp(56f)
                minimumHeight = dp(40f)
                setPadding(dp(14f), 0, dp(14f), 0)
                typeface = if (isArabic) arabicTypeface else latinTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isArabic) 16f else 14f)
                setTextColor(if (isPrimary) theme.accentTextColor else theme.primaryTextColor)
                background = GradientDrawable().apply {
                    cornerRadius = dp(18f).toFloat()
                    setColor(if (isPrimary) theme.accentColor else theme.functionKeyColor)
                }
                setOnClickListener { onCandidateSelected?.invoke(entity) }
                setOnLongClickListener {
                    onCandidateLongPressed?.invoke(entity)
                    onCandidateLongPressed != null
                }
            }
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, dp(36f)).apply {
                marginStart = dp(4f)
                marginEnd = dp(4f)
            }
            stripRow.addView(pill, lp)
        }
    }

    private fun placeholderContainsArabic(value: String): Boolean =
        value.any { it.code in 0x0600..0x077F }

    @SuppressLint("DiscouragedApi")
    private fun loadBundledFont(fontName: String): Typeface? {
        return try {
            val id = context.resources.getIdentifier(fontName, "font", context.packageName)
            if (id != 0) ResourcesCompat.getFont(context, id) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    companion object {
        const val DEFAULT_MAX_CANDIDATES = 5
    }
}
