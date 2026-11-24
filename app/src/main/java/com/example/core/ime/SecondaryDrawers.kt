package com.example.core.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.database.ClipboardEntryEntity
import com.example.core.preferences.ErgonomicMode
import com.example.core.preferences.KeyboardSettings
import com.example.core.preferences.KeyboardThemePreset

class EmojiMediaDrawerView(
    context: Context,
    private val onCommitText: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onCloseDrawer: () -> Unit
) : LinearLayout(context) {

    private val arabicTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_naskh_arabic) ?: Typeface.DEFAULT
    }

    private var theme: ResolvedKeyboardTheme = KeyboardThemeTokens.resolve(context, KeyboardSettings())
    private var selectedCategory = EmojiCategory.SMILEYS
    private val recentEmojis = ArrayDeque<String>(16)

    private val searchInput: EditText
    private val categoryTabsRow: LinearLayout
    private val gridContainer: GridLayout
    private val backBtn: TextView
    private val deleteBtn: TextView
    private var skinPopup: PopupWindow? = null

    init {
        orientation = VERTICAL
        setPadding(dp(6f), dp(6f), dp(6f), dp(6f))

        // Top search + close row
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        backBtn = TextView(context).apply {
            text = "ABC"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            setOnClickListener { onCloseDrawer() }
        }
        searchInput = EditText(context).apply {
            hint = "Search emoji (smile, tea, multan…)"
            setSingleLine(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderGrid(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        deleteBtn = TextView(context).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            setOnClickListener { onBackspace() }
        }
        topRow.addView(backBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40f)))
        topRow.addView(searchInput, LayoutParams(0, dp(40f), 1f).apply { marginStart = dp(6f); marginEnd = dp(6f) })
        topRow.addView(deleteBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40f)))
        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // Category tabs
        categoryTabsRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val tabsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(categoryTabsRow)
        }
        addView(tabsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(40f)).apply { topMargin = dp(4f) })

        // Grid inside ScrollView
        gridContainer = GridLayout(context).apply {
            columnCount = 7
        }
        val scroll = ScrollView(context).apply {
            addView(gridContainer, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(196f)))

        buildCategoryTabs()
        renderGrid("")
    }

    fun applyTheme(resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.backgroundColor)
        backBtn.setTextColor(resolved.primaryTextColor)
        backBtn.background = pillDrawable(resolved.functionKeyColor, dp(8f).toFloat())
        deleteBtn.setTextColor(resolved.primaryTextColor)
        deleteBtn.background = pillDrawable(resolved.functionKeyColor, dp(8f).toFloat())
        searchInput.setTextColor(resolved.primaryTextColor)
        searchInput.setHintTextColor(resolved.hintTextColor)
        searchInput.background = pillDrawable(resolved.functionKeyColor, dp(10f).toFloat())
        buildCategoryTabs()
        renderGrid(searchInput.text?.toString().orEmpty())
    }

    private fun buildCategoryTabs() {
        categoryTabsRow.removeAllViews()
        EmojiCategory.entries.forEach { cat ->
            val selected = cat == selectedCategory
            val tab = TextView(context).apply {
                text = cat.icon
                contentDescription = cat.title
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (cat == EmojiCategory.KAOMOJI) 12f else 17f)
                setTextColor(if (selected) theme.accentTextColor else theme.primaryTextColor)
                background = if (selected) pillDrawable(theme.accentColor, dp(10f).toFloat()) else null
                setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
                setOnClickListener {
                    selectedCategory = cat
                    buildCategoryTabs()
                    renderGrid(searchInput.text?.toString().orEmpty())
                }
            }
            categoryTabsRow.addView(tab, LayoutParams(LayoutParams.WRAP_CONTENT, dp(36f)).apply { marginEnd = dp(4f) })
        }
    }

    private fun renderGrid(query: String) {
        gridContainer.removeAllViews()
        if (query.isNotBlank()) {
            gridContainer.columnCount = 7
            val results = EmojiCatalog.search(query)
            if (results.isEmpty()) {
                addEmptyMessage("No matching emojis")
                return
            }
            results.forEach { addEmojiCell(it) }
            return
        }

        if (selectedCategory == EmojiCategory.KAOMOJI) {
            gridContainer.columnCount = 2
            EmojiCatalog.kaomojiAndStickers.forEach { sticker ->
                val tv = TextView(context).apply {
                    text = sticker
                    typeface = arabicTypeface
                    gravity = Gravity.CENTER
                    setTextColor(theme.primaryTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    background = pillDrawable(theme.primaryKeyColor, dp(8f).toFloat())
                    setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
                    setOnClickListener { onCommitText(sticker) }
                }
                val lp = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
                }
                gridContainer.addView(tv, lp)
            }
            return
        }

        gridContainer.columnCount = 7
        val list = if (selectedCategory == EmojiCategory.RECENT) {
            recentEmojis.map { EmojiEntry(it, "") }
        } else {
            EmojiCatalog.byCategory[selectedCategory].orEmpty()
        }
        if (list.isEmpty()) {
            addEmptyMessage("Type or tap an emoji to populate Recents")
            return
        }
        list.forEach { addEmojiCell(it) }
    }

    private fun addEmptyMessage(msg: String) {
        gridContainer.columnCount = 1
        val tv = TextView(context).apply {
            text = msg
            gravity = Gravity.CENTER
            setTextColor(theme.hintTextColor)
            setPadding(dp(16f), dp(24f), dp(16f), dp(24f))
        }
        gridContainer.addView(tv, GridLayout.LayoutParams().apply {
            width = GridLayout.LayoutParams.MATCH_PARENT
        })
    }

    private fun addEmojiCell(entry: EmojiEntry) {
        val tv = TextView(context).apply {
            text = entry.char
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, dp(6f), 0, dp(6f))
            setOnClickListener {
                recordRecent(entry.char)
                onCommitText(entry.char)
            }
            if (entry.supportsSkinTone) {
                setOnLongClickListener { view ->
                    showSkinTonePopup(view, entry.char)
                    true
                }
            }
        }
        val lp = GridLayout.LayoutParams().apply {
            width = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        gridContainer.addView(tv, lp)
    }

    private fun showSkinTonePopup(anchor: View, baseEmoji: String) {
        skinPopup?.dismiss()
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = pillDrawable(theme.popupBackgroundColor, dp(12f).toFloat())
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
            elevation = dp(10f).toFloat()
        }
        EmojiCatalog.skinTones.forEach { tone ->
            val variant = baseEmoji + tone
            val btn = TextView(context).apply {
                text = variant
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                setOnClickListener {
                    skinPopup?.dismiss()
                    recordRecent(variant)
                    onCommitText(variant)
                }
            }
            row.addView(btn)
        }
        val pop = PopupWindow(row, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(10f).toFloat()
        }
        skinPopup = pop
        pop.showAsDropDown(anchor, -dp(40f), -dp(92f))
    }

    private fun recordRecent(emoji: String) {
        recentEmojis.remove(emoji)
        recentEmojis.addFirst(emoji)
        if (recentEmojis.size > 21) recentEmojis.removeLast()
    }

    private fun pillDrawable(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

class ClipboardDrawerView(
    context: Context,
    private val onPasteText: (String) -> Unit,
    private val onTogglePin: (Long, Boolean) -> Unit,
    private val onDeleteItem: (Long) -> Unit,
    private val onClearUnpinned: () -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private var theme: ResolvedKeyboardTheme = KeyboardThemeTokens.resolve(context, KeyboardSettings())
    private val listContainer: LinearLayout
    private val titleView: TextView
    private val clearBtn: TextView
    private val closeBtn: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(8f), dp(10f), dp(8f))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = TextView(context).apply {
            text = "Clipboard"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        }
        clearBtn = TextView(context).apply {
            text = "Clear"
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onClearUnpinned() }
        }
        closeBtn = TextView(context).apply {
            text = "✕"
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onClose() }
        }
        header.addView(titleView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(clearBtn)
        header.addView(closeBtn)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        listContainer = LinearLayout(context).apply { orientation = VERTICAL }
        val scroll = ScrollView(context).apply {
            addView(listContainer, ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(220f)).apply { topMargin = dp(6f) })
    }

    fun applyTheme(resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.backgroundColor)
        titleView.setTextColor(resolved.primaryTextColor)
        clearBtn.setTextColor(resolved.accentColor)
        closeBtn.setTextColor(resolved.primaryTextColor)
    }

    fun render(items: List<ClipboardEntryEntity>, isProtectedField: Boolean) {
        listContainer.removeAllViews()
        if (isProtectedField) {
            listContainer.addView(emptyMessage(context.getString(R.string.ime_clipboard_protected)))
            return
        }
        if (items.isEmpty()) {
            listContainer.addView(emptyMessage(context.getString(R.string.ime_clipboard_empty)))
            return
        }
        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10f), dp(8f), dp(8f), dp(8f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(theme.primaryKeyColor)
                }
            }
            val textTv = TextView(context).apply {
                text = item.text
                maxLines = 2
                setTextColor(theme.primaryTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setOnClickListener { onPasteText(item.text) }
            }
            val pinBtn = TextView(context).apply {
                text = if (item.isPinned) "📌" else "📍"
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                setOnClickListener { onTogglePin(item.id, !item.isPinned) }
            }
            val delBtn = TextView(context).apply {
                text = "🗑"
                setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
                setOnClickListener { onDeleteItem(item.id) }
            }
            row.addView(textTv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(pinBtn)
            row.addView(delBtn)
            listContainer.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6f)
            })
        }
    }

    private fun emptyMessage(msg: String) = TextView(context).apply {
        text = msg
        gravity = Gravity.CENTER
        setTextColor(theme.hintTextColor)
        setPadding(dp(16f), dp(40f), dp(16f), dp(40f))
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

class TextEditPadView(
    context: Context,
    private val onDpadMove: (dx: Int, dy: Int, selecting: Boolean) -> Unit,
    private val onWordJump: (left: Boolean, selecting: Boolean) -> Unit,
    private val onHomeEnd: (home: Boolean, selecting: Boolean) -> Unit,
    private val onEditCommand: (EditCmd) -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    enum class EditCmd { SELECT_ALL, CUT, COPY, PASTE }

    private var theme: ResolvedKeyboardTheme = KeyboardThemeTokens.resolve(context, KeyboardSettings())
    private var selecting = false
    private val selectBtn: Button

    init {
        orientation = VERTICAL
        setPadding(dp(10f), dp(10f), dp(10f), dp(10f))

        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "Text Editing & Selection"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val closeBtn = actionBtn("ABC") { onClose() }
        topBar.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(closeBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40f)))
        addView(topBar)

        val grid = GridLayout(context).apply {
            columnCount = 4
            rowCount = 3
        }

        selectBtn = actionBtn("Select") {
            selecting = !selecting
            refreshSelectBtn()
        }

        val cells: List<Pair<String, () -> Unit>> = listOf(
            "⏮ Word" to { onWordJump(true, selecting) },
            "▲" to { onDpadMove(0, -1, selecting) },
            "Word ⏭" to { onWordJump(false, selecting) },
            "Select All" to { onEditCommand(EditCmd.SELECT_ALL) },

            "◀" to { onDpadMove(-1, 0, selecting) },
            "Select" to { selecting = !selecting; refreshSelectBtn() },
            "▶" to { onDpadMove(1, 0, selecting) },
            "Copy" to { onEditCommand(EditCmd.COPY) },

            "Home" to { onHomeEnd(true, selecting) },
            "▼" to { onDpadMove(0, 1, selecting) },
            "End" to { onHomeEnd(false, selecting) },
            "Paste" to { onEditCommand(EditCmd.PASTE) }
        )

        cells.forEachIndexed { idx, (label, action) ->
            val view = if (idx == 5) selectBtn else actionBtn(label, action)
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(52f)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4f), dp(4f), dp(4f), dp(4f))
            }
            grid.addView(view, lp)
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })

        val cutRow = actionBtn("✂ Cut Selection") { onEditCommand(EditCmd.CUT) }
        addView(cutRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(44f)).apply { topMargin = dp(6f) })
    }

    private fun refreshSelectBtn() {
        selectBtn.text = if (selecting) "Selecting ✓" else "Select"
        selectBtn.setBackgroundColor(if (selecting) theme.accentColor else theme.functionKeyColor)
        selectBtn.setTextColor(if (selecting) theme.accentTextColor else theme.primaryTextColor)
    }

    fun applyTheme(resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.backgroundColor)
        applyColorsRecursively(this, resolved)
        refreshSelectBtn()
    }

    private fun applyColorsRecursively(group: ViewGroup, th: ResolvedKeyboardTheme) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is Button -> {
                    child.setTextColor(th.primaryTextColor)
                    child.background = GradientDrawable().apply {
                        cornerRadius = dp(8f).toFloat()
                        setColor(th.primaryKeyColor)
                    }
                }
                is TextView -> child.setTextColor(th.primaryTextColor)
                is ViewGroup -> applyColorsRecursively(child, th)
            }
        }
    }

    private fun actionBtn(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

class VoiceTypingOverlayView(
    context: Context,
    private val onCancel: () -> Unit
) : LinearLayout(context) {

    private val statusTv: TextView
    private val barsRow: LinearLayout
    private val bars = ArrayList<View>(7)
    private var theme: ResolvedKeyboardTheme = KeyboardThemeTokens.resolve(context, KeyboardSettings())

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(24f), dp(16f), dp(24f))

        statusTv = TextView(context).apply {
            text = context.getString(R.string.ime_voice_listening)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        }
        barsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(7) {
            val bar = View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4f).toFloat()
                    setColor(theme.accentColor)
                }
            }
            bars.add(bar)
            barsRow.addView(bar, LayoutParams(dp(8f), dp(28f)).apply { marginStart = dp(4f); marginEnd = dp(4f) })
        }
        val cancelBtn = Button(context).apply {
            text = "Back to Keyboard"
            isAllCaps = false
            setOnClickListener { onCancel() }
        }
        addView(statusTv)
        addView(barsRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(64f)).apply { topMargin = dp(12f) })
        addView(cancelBtn, LayoutParams(LayoutParams.WRAP_CONTENT, dp(44f)).apply { topMargin = dp(14f) })
    }

    fun applyTheme(resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.backgroundColor)
        statusTv.setTextColor(resolved.primaryTextColor)
        bars.forEach {
            (it.background as? GradientDrawable)?.setColor(resolved.accentColor)
        }
    }

    fun updateRms(rmsdB: Float) {
        val norm = ((rmsdB + 2f) / 12f).coerceIn(0.2f, 1f)
        bars.forEachIndexed { idx, bar ->
            val factor = if (idx % 2 == 0) norm else (norm * 0.7f).coerceAtLeast(0.2f)
            bar.layoutParams = (bar.layoutParams as LayoutParams).apply {
                height = dp(14f + factor * 44f)
            }
        }
    }

    fun setStatus(message: String) {
        statusTv.text = message
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

class LiveResizerOverlayView(
    context: Context,
    private val onSizeChanged: (heightPct: Int, widthPct: Int) -> Unit,
    private val onErgonomicChanged: (ErgonomicMode) -> Unit,
    private val onThemeSelected: (KeyboardThemePreset) -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private var theme: ResolvedKeyboardTheme = KeyboardThemeTokens.resolve(context, KeyboardSettings())
    private val heightLabel: TextView
    private val widthLabel: TextView
    private val heightSeek: SeekBar
    private val widthSeek: SeekBar
    private val modeRow: LinearLayout
    private val themesRow: LinearLayout

    init {
        orientation = VERTICAL
        setPadding(dp(12f), dp(10f), dp(12f), dp(10f))

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "Resize, Layout & Themes"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val doneBtn = Button(context).apply {
            text = "Done"
            isAllCaps = false
            setOnClickListener { onClose() }
        }
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(doneBtn)
        addView(header)

        heightLabel = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
        heightSeek = SeekBar(context).apply { max = 70 } // 60..130
        widthLabel = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
        widthSeek = SeekBar(context).apply { max = 30 } // 70..100

        heightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val h = 60 + progress
                    heightLabel.text = "Keyboard Height: $h%"
                    onSizeChanged(h, 70 + widthSeek.progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        widthSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val w = 70 + progress
                    widthLabel.text = "Keyboard Width: $w%"
                    onSizeChanged(60 + heightSeek.progress, w)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        addView(heightLabel)
        addView(heightSeek)
        addView(widthLabel)
        addView(widthSeek)

        modeRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val modeScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(modeRow)
        }
        addView(modeScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })

        themesRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        val themesScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(themesRow)
        }
        addView(themesScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })
    }

    fun bind(settings: KeyboardSettings, resolved: ResolvedKeyboardTheme) {
        theme = resolved
        setBackgroundColor(resolved.backgroundColor)
        heightLabel.text = "Keyboard Height: ${settings.heightPercent}%"
        heightLabel.setTextColor(resolved.primaryTextColor)
        widthLabel.text = "Keyboard Width: ${settings.widthPercent}%"
        widthLabel.setTextColor(resolved.primaryTextColor)
        heightSeek.progress = (settings.heightPercent - 60).coerceIn(0, 70)
        widthSeek.progress = (settings.widthPercent - 70).coerceIn(0, 30)

        modeRow.removeAllViews()
        ErgonomicMode.entries.forEach { mode ->
            val selected = mode == settings.ergonomicMode
            val chip = TextView(context).apply {
                text = mode.displayName
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                setTextColor(if (selected) resolved.accentTextColor else resolved.primaryTextColor)
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(if (selected) resolved.accentColor else resolved.primaryKeyColor)
                }
                setOnClickListener { onErgonomicChanged(mode) }
            }
            modeRow.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6f) })
        }

        themesRow.removeAllViews()
        KeyboardThemePreset.entries.forEach { preset ->
            val selected = preset == settings.themePreset
            val chip = TextView(context).apply {
                text = preset.displayName
                setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
                setTextColor(if (selected) resolved.accentTextColor else resolved.primaryTextColor)
                background = GradientDrawable().apply {
                    cornerRadius = dp(10f).toFloat()
                    setColor(if (selected) resolved.accentColor else resolved.functionKeyColor)
                }
                setOnClickListener { onThemeSelected(preset) }
            }
            themesRow.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6f) })
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
