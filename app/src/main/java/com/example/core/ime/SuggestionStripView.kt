package com.example.core.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.linguistics.SuggestionCandidate

enum class ToolbarTool(val icon: String, val label: String) {
    AI_ASSISTANT("✨", "AI Assist"),
    CLIPBOARD("📋", "Clipboard"),
    THEMES("🎨", "Themes"),
    RESIZE("⇕", "Resize"),
    ONE_HANDED("✋", "One-Hand"),
    FLOATING("⧉", "Float"),
    VOICE("🎙", "Voice"),
    EDIT_PAD("✥", "Edit"),
    SETTINGS("⚙", "Settings")
}

interface SuggestionStripListener {
    fun onSuggestionTapped(candidate: SuggestionCandidate)
    fun onForgetWordRequested(candidate: SuggestionCandidate)
    fun onUndoDeleteTapped()
    fun onToolbarToolTapped(tool: ToolbarTool)
}

class SuggestionStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var listener: SuggestionStripListener? = null

    private val arabicTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_naskh_arabic) ?: Typeface.DEFAULT
    }
    private val latinTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
    }

    private var theme: ResolvedKeyboardTheme? = null
    private var showToolsBar: Boolean = false
    private var isIncognito: Boolean = false
    private var undoAvailable: Boolean = false
    private var forgetPromptCandidate: SuggestionCandidate? = null
    private var currentCandidates: List<SuggestionCandidate> = emptyList()

    private val toggleToolsBtn: TextView
    private val contentSlot: LinearLayout
    private val toolsScroll: HorizontalScrollView
    private val toolsRow: LinearLayout

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6f), dp(4f), dp(6f), dp(4f))

        toggleToolsBtn = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "⊞"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            minimumWidth = dp(40f)
            minimumHeight = dp(38f)
            setOnClickListener {
                showToolsBar = !showToolsBar
                forgetPromptCandidate = null
                renderContent()
            }
        }
        addView(toggleToolsBtn, LayoutParams(dp(40f), dp(38f)))

        contentSlot = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(contentSlot, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        toolsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ToolbarTool.entries.forEach { tool ->
            val btn = TextView(context).apply {
                text = tool.icon
                contentDescription = tool.label
                gravity = Gravity.CENTER
                setPadding(dp(10f), 0, dp(10f), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickListener { listener?.onToolbarToolTapped(tool) }
            }
            toolsRow.addView(btn, LayoutParams(dp(46f), dp(38f)))
        }
        toolsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(toolsRow)
            visibility = View.GONE
        }
        addView(toolsScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    fun applyTheme(resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.stripBackgroundColor)
        toggleToolsBtn.setTextColor(resolved.hintTextColor)
        for (i in 0 until toolsRow.childCount) {
            (toolsRow.getChildAt(i) as? TextView)?.setTextColor(resolved.primaryTextColor)
        }
        renderContent()
    }

    fun updateSuggestions(
        candidates: List<SuggestionCandidate>,
        incognito: Boolean,
        hasUndoDelete: Boolean
    ) {
        currentCandidates = candidates
        isIncognito = incognito
        undoAvailable = hasUndoDelete
        if (candidates.isNotEmpty() && !showToolsBar) {
            forgetPromptCandidate = null
        }
        renderContent()
    }

    fun setToolsExpanded(expanded: Boolean) {
        showToolsBar = expanded
        renderContent()
    }

    private fun renderContent() {
        val th = theme ?: return
        toggleToolsBtn.text = if (showToolsBar) "✕" else if (isIncognito) "🕵" else "⊞"
        toggleToolsBtn.setTextColor(if (isIncognito) th.accentColor else th.hintTextColor)

        if (showToolsBar) {
            contentSlot.visibility = View.GONE
            toolsScroll.visibility = View.VISIBLE
            return
        }

        toolsScroll.visibility = View.GONE
        contentSlot.visibility = View.VISIBLE
        contentSlot.removeAllViews()

        // 1. One-tap Forget Word confirmation pill
        val forgetTarget = forgetPromptCandidate
        if (forgetTarget != null) {
            val confirmBtn = buildPillView(
                label = "Remove \"${forgetTarget.text}\"",
                isHighlighted = true,
                th = th
            ) {
                listener?.onForgetWordRequested(forgetTarget)
                forgetPromptCandidate = null
                renderContent()
            }
            val cancelBtn = buildPillView("Cancel", isHighlighted = false, th = th) {
                forgetPromptCandidate = null
                renderContent()
            }
            contentSlot.addView(confirmBtn, LayoutParams(0, dp(36f), 2f).apply { marginEnd = dp(6f) })
            contentSlot.addView(cancelBtn, LayoutParams(0, dp(36f), 1f))
            return
        }

        // 2. Undo Gesture-Delete chip
        if (undoAvailable) {
            val undoBtn = buildPillView("↶ Undo delete", isHighlighted = true, th = th) {
                listener?.onUndoDeleteTapped()
            }
            contentSlot.addView(undoBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36f)).apply { marginEnd = dp(6f) })
        }

        // 3. Incognito status or Suggestions
        if (currentCandidates.isEmpty()) {
            val info = TextView(context).apply {
                text = if (isIncognito) {
                    context.getString(R.string.ime_incognito_active)
                } else {
                    "Saraiki Pro • Tap ⊞ for tools"
                }
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(th.hintTextColor)
                typeface = latinTypeface
            }
            contentSlot.addView(info, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            return
        }

        currentCandidates.take(4).forEachIndexed { idx, candidate ->
            val pill = buildPillView(
                label = candidate.text,
                isHighlighted = candidate.isAutocorrect || (idx == 1 && !candidate.isEmoji),
                th = th,
                onClick = { listener?.onSuggestionTapped(candidate) },
                onLongClick = {
                    if (candidate.isLearned && !candidate.isEmoji) {
                        forgetPromptCandidate = candidate
                        renderContent()
                        true
                    } else false
                }
            )
            val lp = if (candidate.isEmoji) {
                LayoutParams(dp(44f), dp(36f)).apply { marginStart = dp(4f) }
            } else {
                LayoutParams(0, dp(36f), 1f).apply {
                    marginStart = dp(3f)
                    marginEnd = dp(3f)
                }
            }
            contentSlot.addView(pill, lp)
        }
    }

    private fun buildPillView(
        label: String,
        isHighlighted: Boolean,
        th: ResolvedKeyboardTheme,
        onLongClick: (() -> Boolean)? = null,
        onClick: () -> Unit
    ): TextView {
        val isArabic = label.any { it.code in 0x0600..0x077F }
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = if (isArabic) arabicTypeface else latinTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isArabic) 16f else 14f)
            setTextColor(if (isHighlighted) th.accentTextColor else th.primaryTextColor)
            setPadding(dp(8f), 0, dp(8f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(if (isHighlighted) th.accentColor else th.functionKeyColor)
            }
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener { onLongClick() }
            }
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
