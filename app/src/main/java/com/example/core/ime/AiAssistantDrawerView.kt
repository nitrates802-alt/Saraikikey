package com.example.core.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.ai.AiRepository
import com.example.core.ai.AiTaskType
import com.example.core.ai.PlaceholderAiRepository
import com.example.core.preferences.KeyboardLanguage
import com.example.core.preferences.KeyboardSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiAssistantDrawerView(
    context: Context,
    private val aiRepository: AiRepository,
    private val onInsertText: (String) -> Unit,
    private val onCloseDrawer: () -> Unit
) : LinearLayout(context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val arabicTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_naskh_arabic) ?: Typeface.DEFAULT
    }
    private val latinTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.SANS_SERIF
    }

    private var theme: ResolvedKeyboardTheme = KeyboardThemeTokens.resolve(context, KeyboardSettings())
    private var targetLanguage: KeyboardLanguage = KeyboardLanguage.SARAIKI

    private val headerRow: LinearLayout
    private val backBtn: TextView
    private val titleLabel: TextView
    private val noticeLabel: TextView
    private val langRow: LinearLayout
    private val inputEdit: EditText
    private val tasksRow: LinearLayout
    private val resultBox: TextView
    private val insertBtn: TextView

    private var lastGeneratedText: String = ""

    init {
        orientation = VERTICAL
        setPadding(dp(8f), dp(6f), dp(8f), dp(8f))

        headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backBtn = TextView(context).apply {
            text = "ABC"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onCloseDrawer() }
        }
        titleLabel = TextView(context).apply {
            text = "✨ AI Writing Assistant"
            typeface = latinTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8f), 0, dp(8f), 0)
        }
        insertBtn = TextView(context).apply {
            text = "Insert"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            isEnabled = false
            alpha = 0.5f
            setOnClickListener {
                if (lastGeneratedText.isNotBlank()) {
                    onInsertText(lastGeneratedText)
                }
            }
        }
        headerRow.addView(backBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f)))
        headerRow.addView(titleLabel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(insertBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f)))
        addView(headerRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        noticeLabel = TextView(context).apply {
            text = PlaceholderAiRepository.NOTICE_FIREBASE_REQUIRED
            typeface = latinTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(8f), dp(5f), dp(8f), dp(5f))
        }
        addView(noticeLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4f) })

        langRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(langRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) })

        inputEdit = EditText(context).apply {
            hint = context.getString(R.string.ai_drawer_input_hint)
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
        }
        addView(inputEdit, LayoutParams(LayoutParams.MATCH_PARENT, dp(38f)).apply { topMargin = dp(6f) })

        tasksRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val tasksScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tasksRow)
        }
        addView(tasksScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(38f)).apply { topMargin = dp(6f) })

        resultBox = TextView(context).apply {
            text = context.getString(R.string.ai_drawer_empty_output)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        val resultScroll = ScrollView(context).apply {
            addView(resultBox, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(resultScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(88f)).apply { topMargin = dp(6f) })

        buildLanguageChips()
        buildTaskButtons()
    }

    fun prepare(seedText: String, activeLanguage: KeyboardLanguage) {
        targetLanguage = activeLanguage
        if (seedText.isNotBlank()) {
            inputEdit.setText(seedText)
            inputEdit.setSelection(seedText.length)
        }
        buildLanguageChips()
        applyTheme(theme)
    }

    fun applyTheme(resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.backgroundColor)
        backBtn.setTextColor(resolved.primaryTextColor)
        backBtn.background = pillDrawable(resolved.functionKeyColor, dp(8f).toFloat())
        titleLabel.setTextColor(resolved.primaryTextColor)
        insertBtn.setTextColor(resolved.accentTextColor)
        insertBtn.background = pillDrawable(resolved.accentColor, dp(8f).toFloat())
        noticeLabel.setTextColor(resolved.hintTextColor)
        noticeLabel.background = pillDrawable(resolved.stripBackgroundColor, dp(8f).toFloat())
        inputEdit.setTextColor(resolved.primaryTextColor)
        inputEdit.setHintTextColor(resolved.hintTextColor)
        inputEdit.background = pillDrawable(resolved.functionKeyColor, dp(8f).toFloat())
        resultBox.setTextColor(resolved.primaryTextColor)
        resultBox.background = pillDrawable(resolved.primaryKeyColor, dp(8f).toFloat())
        buildLanguageChips()
        buildTaskButtons()
    }

    private fun buildLanguageChips() {
        langRow.removeAllViews()
        KeyboardLanguage.entries.forEach { lang ->
            val selected = lang == targetLanguage
            val chip = TextView(context).apply {
                text = lang.nativeName
                gravity = Gravity.CENTER
                typeface = if (lang.isRtl) arabicTypeface else latinTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(if (selected) theme.accentTextColor else theme.primaryTextColor)
                background = pillDrawable(if (selected) theme.accentColor else theme.functionKeyColor, dp(10f).toFloat())
                setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
                setOnClickListener {
                    targetLanguage = lang
                    buildLanguageChips()
                }
            }
            langRow.addView(chip, LayoutParams(0, dp(30f), 1f).apply { marginEnd = dp(4f) })
        }
    }

    private fun buildTaskButtons() {
        tasksRow.removeAllViews()
        AiTaskType.entries.forEach { task ->
            val btn = TextView(context).apply {
                text = task.shortLabel
                gravity = Gravity.CENTER
                typeface = latinTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(theme.primaryTextColor)
                background = pillDrawable(theme.functionKeyColor, dp(10f).toFloat())
                setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
                setOnClickListener { runTask(task) }
            }
            tasksRow.addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(34f)).apply { marginEnd = dp(6f) })
        }
    }

    private fun runTask(task: AiTaskType) {
        val raw = inputEdit.text?.toString().orEmpty()
        resultBox.text = "Generating sample output…"
        scope.launch {
            val result = aiRepository.runTask(task, raw, targetLanguage)
            lastGeneratedText = result.outputText
            val isArabic = result.outputText.any { it.code in 0x0600..0x077F }
            resultBox.typeface = if (isArabic) arabicTypeface else latinTypeface
            resultBox.text = result.outputText
            noticeLabel.text = result.notice
            insertBtn.isEnabled = true
            insertBtn.alpha = 1f
        }
    }

    private fun pillDrawable(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius
            setColor(color)
        }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
