package com.example.core.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Hardware-accelerated custom [View] in `:core-ime` that measures, lays out, and renders
 * [KeyboardKey] rows on a [Canvas], handles multi-touch / long-press popups / glide typing /
 * spacebar cursor trackpad / gesture delete, and emits typed [KeyboardInteractionListener] events.
 */
class CanvasKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var listener: KeyboardInteractionListener? = null

    private var rows: List<List<KeyboardKey>> = emptyList()
    private var keysWithBounds: List<KeyboardKey> = emptyList()
    private var options: KeyboardViewOptions = KeyboardViewOptions()
    private var theme: ResolvedKeyboardTheme = ResolvedKeyboardTheme.defaultDark()

    private var customArabicTypeface: Typeface? = null
    private var customLatinTypeface: Typeface? = null

    private val arabicTypeface: Typeface by lazy {
        customArabicTypeface ?: loadBundledFont("noto_naskh_arabic") ?: Typeface.DEFAULT
    }
    private val latinTypeface: Typeface by lazy {
        customLatinTypeface ?: loadBundledFont("plus_jakarta_sans") ?: Typeface.SANS_SERIF
    }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dimPaint = Paint()

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // Active pointer & gesture tracking
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var activeKey: KeyboardKey? = null
    private var downX = 0f
    private var downY = 0f
    private var lastTrackpadX = 0f
    private var trackpadRemainder = 0f
    private var isSpacebarTrackpad = false
    private var isGestureDeleting = false
    private var gestureDeleteAccumX = 0f
    private var isGliding = false
    private val glidePoints = ArrayList<GlidePoint>(64)
    private val glidePath = Path()

    // Popups
    private var previewPopup: PopupWindow? = null
    private var previewTextView: TextView? = null

    private var longPressPopup: PopupWindow? = null
    private var longPressContainer: LinearLayout? = null
    private var activePopupSpec: Pair<KeyboardKey, List<String>>? = null
    private var selectedPopupIndex: Int = -1
    private var isLongPressPopupOpen = false
    private var backspaceStepCount = 0

    // Wallpaper cache
    private var cachedWallpaperUri: String? = null
    private var cachedWallpaperBitmap: Bitmap? = null

    private val longPressRunnable = Runnable {
        val target = activeKey ?: return@Runnable
        if (isGliding || isSpacebarTrackpad || isGestureDeleting) return@Runnable

        when (target.action) {
            KeyAction.Space -> {
                dismissPreviewPopup()
                listener?.onSpacebarLongPress()
                activeKey = null
            }
            KeyAction.Backspace -> {
                backspaceStepCount = 0
                handler.post(backspaceRepeatRunnable)
            }
            else -> {
                if (target.longPressAlternatives.isNotEmpty()) {
                    dismissPreviewPopup()
                    showLongPressPopup(target)
                }
            }
        }
    }

    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            val target = activeKey
            if (target?.action == KeyAction.Backspace && !isGestureDeleting) {
                backspaceStepCount++
                val deleteWord = backspaceStepCount > 14
                listener?.onBackspaceRepeatStep(deleteWord)
                val interval = if (backspaceStepCount > 10) 42L else 68L
                handler.postDelayed(this, interval)
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isHapticFeedbackEnabled = false
    }

    fun setTypefaces(arabic: Typeface?, latin: Typeface?) {
        customArabicTypeface = arabic
        customLatinTypeface = latin
        invalidate()
    }

    fun updateState(
        newRows: List<List<KeyboardKey>>,
        newTheme: ResolvedKeyboardTheme = theme
    ) {
        updateState(newRows, options, newTheme)
    }

    fun updateState(
        newRows: List<List<KeyboardKey>>,
        newOptions: KeyboardViewOptions,
        newTheme: ResolvedKeyboardTheme
    ) {
        rows = newRows
        options = newOptions
        theme = newTheme
        loadWallpaperIfNeeded(newTheme.wallpaperUri)
        requestLayout()
        computeKeyBounds(width, height)
        invalidate()
    }

    fun getMeasuredKeys(): List<KeyboardKey> = keysWithBounds

    @SuppressLint("DiscouragedApi")
    private fun loadBundledFont(fontName: String): Typeface? {
        return try {
            val id = context.resources.getIdentifier(fontName, "font", context.packageName)
            if (id != 0) ResourcesCompat.getFont(context, id) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun loadWallpaperIfNeeded(uriString: String?) {
        if (uriString == cachedWallpaperUri) return
        cachedWallpaperUri = uriString
        cachedWallpaperBitmap?.recycle()
        cachedWallpaperBitmap = null
        if (uriString.isNullOrBlank()) return
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                cachedWallpaperBitmap = BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {
            cachedWallpaperBitmap = null
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availWidth = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = rows.size.coerceAtLeast(4)
        val baseRowHeightDp = 50f * (options.heightPercent / 100f)
        val rowHeightPx = dp(baseRowHeightDp)
        val verticalPadPx = dp(10f)
        val desiredHeight = (rowCount * rowHeightPx + verticalPadPx).roundToInt()
        setMeasuredDimension(availWidth, desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyBounds(w, h)
    }

    private fun computeKeyBounds(totalW: Int, totalH: Int) {
        if (totalW <= 0 || totalH <= 0 || rows.isEmpty()) {
            keysWithBounds = emptyList()
            return
        }
        val hGap = dp(4.5f)
        val vGap = dp(6f)
        val outerPadH = dp(5f)
        val outerPadTop = dp(5f)
        val outerPadBottom = dp(6f)

        val usableW = (totalW - outerPadH * 2).coerceAtLeast(1f)
        val usableH = (totalH - outerPadTop - outerPadBottom).coerceAtLeast(1f)
        val totalHeightWeight = rows.sumOf { row -> (row.maxOfOrNull { it.heightWeight } ?: 1f).toDouble() }
            .toFloat().coerceAtLeast(1f)
        val availableRowHeight = (usableH - vGap * (rows.size - 1)).coerceAtLeast(1f)

        val computed = ArrayList<KeyboardKey>(48)
        var cursorY = outerPadTop
        rows.forEach { row ->
            val rowWeight = row.maxOfOrNull { it.heightWeight } ?: 1f
            val rowHeight = availableRowHeight * (rowWeight / totalHeightWeight)
            val totalWeight = row.sumOf { it.widthWeight.toDouble() }.toFloat().coerceAtLeast(1f)
            val totalGaps = hGap * (row.size - 1)
            val rowContentW = (usableW - totalGaps).coerceAtLeast(1f)
            var cursorX = outerPadH

            row.forEach { key ->
                val keyW = rowContentW * (key.widthWeight / totalWeight)
                if (key.action != KeyAction.Spacer) {
                    computed.add(key.withBounds(x = cursorX, y = cursorY, width = keyW, height = rowHeight))
                }
                cursorX += keyW + hGap
            }
            cursorY += rowHeight + vGap
        }
        keysWithBounds = computed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Background / Wallpaper
        canvas.drawColor(theme.backgroundColor)
        cachedWallpaperBitmap?.let { bmp ->
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = Rect(0, 0, width, height)
            canvas.drawBitmap(bmp, src, dst, null)
            dimPaint.color = Color.argb((theme.wallpaperDim.coerceIn(0f, 0.92f) * 255).toInt(), 0, 0, 0)
            canvas.drawRect(dst, dimPaint)
        }

        val radius = dp(theme.cornerRadiusDp)
        val borderStroke = dp(1f)
        val mainTextPx = sp(theme.labelFontSizeSp)
        val funcTextPx = sp((theme.labelFontSizeSp * 0.74f).coerceIn(12f, 17f))
        val hintTextPx = sp(10.5f)
        val shadowOffset = dp(1.5f)

        // 2. Keys
        for (key in keysWithBounds) {
            val isPressed = activeKey === key && !isGliding
            val r = key.toRectF()

            // Key drop shadow
            shadowPaint.color = Color.argb(45, 0, 0, 0)
            canvas.drawRoundRect(r.left, r.top + shadowOffset, r.right, r.bottom + shadowOffset, radius, radius, shadowPaint)

            // Key fill
            keyPaint.color = when {
                key.isAccent -> theme.accentColor
                key.isFunctional -> if (isPressed) theme.functionKeyPressedColor else theme.functionKeyColor
                else -> if (isPressed) theme.primaryKeyPressedColor else theme.primaryKeyColor
            }
            canvas.drawRoundRect(r, radius, radius, keyPaint)

            if (theme.showKeyBorders) {
                borderPaint.color = theme.borderColor
                borderPaint.strokeWidth = borderStroke
                canvas.drawRoundRect(r, radius, radius, borderPaint)
            }

            // Primary Label
            val isArabic = key.character.any { it.code in 0x0600..0x077F }
            textPaint.typeface = if (isArabic) (customArabicTypeface ?: arabicTypeface) else (customLatinTypeface ?: latinTypeface)
            textPaint.color = when {
                key.isAccent -> theme.accentTextColor
                key.action == KeyAction.Space -> theme.hintTextColor
                else -> theme.primaryTextColor
            }
            textPaint.textSize = when {
                key.action == KeyAction.Space -> sp(13.5f)
                key.isFunctional -> funcTextPx
                else -> mainTextPx
            }
            val fm = textPaint.fontMetrics
            val textY = r.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(key.character, r.centerX(), textY, textPaint)

            // Secondary Hint Label (top-right corner)
            val hint = key.hintLabel
            if (theme.showSecondaryHints && !hint.isNullOrEmpty()) {
                val hintIsArabic = hint.any { it.code in 0x0600..0x077F }
                hintPaint.typeface = if (hintIsArabic) (customArabicTypeface ?: arabicTypeface) else (customLatinTypeface ?: latinTypeface)
                hintPaint.color = theme.hintTextColor
                hintPaint.textSize = hintTextPx
                canvas.drawText(
                    hint,
                    r.right - dp(5f),
                    r.top + dp(12f),
                    hintPaint
                )
            }
        }

        // 3. Bézier Glide Trail
        if (isGliding && options.showGlideTrail && glidePoints.size >= 2) {
            trailPaint.color = theme.accentColor
            trailPaint.alpha = 215
            trailPaint.strokeWidth = dp(5.5f)
            canvas.drawPath(glidePath, trailPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                downX = event.getX(idx)
                downY = event.getY(idx)
                lastTrackpadX = downX
                trackpadRemainder = 0f
                gestureDeleteAccumX = 0f
                isSpacebarTrackpad = false
                isGestureDeleting = false
                isGliding = false
                isLongPressPopupOpen = false
                glidePoints.clear()
                glidePath.reset()

                val hit = findKeyAt(downX, downY)
                activeKey = hit
                if (hit != null) {
                    listener?.onKeyPressed(hit)
                    if (options.showKeypressPreview && !hit.isFunctional && hit.action != KeyAction.Space) {
                        showPreviewPopup(hit)
                    }
                    if (hit.action is KeyAction.CommitChar && options.glideTypingEnabled) {
                        glidePoints.add(GlidePoint(downX, downY, event.eventTime))
                        glidePath.moveTo(downX, downY)
                    }
                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(longPressRunnable, options.longPressDelayMs.toLong())
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val ptrIdx = event.findPointerIndex(activePointerId)
                if (ptrIdx < 0) return true
                val x = event.getX(ptrIdx)
                val y = event.getY(ptrIdx)
                val dx = x - downX
                val dy = y - downY

                // 1. If long-press popup is open, finger-glide across popup items
                if (isLongPressPopupOpen) {
                    updateLongPressSelection(event.rawX)
                    return true
                }

                val currentKey = activeKey ?: return true

                // 2. Spacebar Cursor Trackpad
                if (currentKey.action == KeyAction.Space && options.spacebarCursorTrackpad) {
                    if (!isSpacebarTrackpad && abs(dx) > touchSlop * 1.3f) {
                        isSpacebarTrackpad = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (isSpacebarTrackpad) {
                        val deltaX = x - lastTrackpadX + trackpadRemainder
                        val stepPx = dp(14f)
                        val steps = (deltaX / stepPx).toInt()
                        if (steps != 0) {
                            trackpadRemainder = deltaX - steps * stepPx
                            listener?.onSpacebarCursorDelta(steps)
                        } else {
                            trackpadRemainder = deltaX
                        }
                        lastTrackpadX = x
                        return true
                    }
                }

                // 3. Gesture Delete from Backspace
                if (currentKey.action == KeyAction.Backspace && options.gestureDeleteEnabled) {
                    if (!isGestureDeleting && dx < -touchSlop * 1.5f) {
                        isGestureDeleting = true
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(backspaceRepeatRunnable)
                        gestureDeleteAccumX = x
                    }
                    if (isGestureDeleting) {
                        val step = dp(26f)
                        if (gestureDeleteAccumX - x >= step) {
                            gestureDeleteAccumX = x
                            listener?.onGestureDeleteWord()
                        }
                        return true
                    }
                }

                // 4. Bézier Glide Typing
                if (options.glideTypingEnabled && currentKey.action is KeyAction.CommitChar) {
                    val distFromDown = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (!isGliding && distFromDown > touchSlop * 2.2f) {
                        isGliding = true
                        handler.removeCallbacks(longPressRunnable)
                        dismissPreviewPopup()
                    }
                    if (isGliding) {
                        val prev = glidePoints.lastOrNull()
                        if (prev == null || hypot((x - prev.x).toDouble(), (y - prev.y).toDouble()) > 6.0) {
                            if (prev != null) {
                                glidePath.quadTo(prev.x, prev.y, (prev.x + x) / 2f, (prev.y + y) / 2f)
                            }
                            glidePoints.add(GlidePoint(x, y, event.eventTime))
                            invalidate()
                        }
                        return true
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(backspaceRepeatRunnable)
                dismissPreviewPopup()

                if (isLongPressPopupOpen) {
                    val items = activePopupSpec?.second.orEmpty()
                    val committed = items.getOrNull(selectedPopupIndex)
                    dismissLongPressPopup()
                    if (event.actionMasked == MotionEvent.ACTION_UP && committed != null) {
                        listener?.onPopupItemCommitted(committed)
                    }
                    resetTouchState()
                    return true
                }

                if (isGliding && event.actionMasked == MotionEvent.ACTION_UP) {
                    val centers = keysWithBounds.map { KeyCenter(it.character, it.centerX, it.centerY) }
                    listener?.onGlideCompleted(glidePoints.toList(), centers)
                    resetTouchState()
                    return true
                }

                val target = activeKey
                if (event.actionMasked == MotionEvent.ACTION_UP &&
                    target != null &&
                    !isSpacebarTrackpad &&
                    !isGestureDeleting &&
                    backspaceStepCount == 0
                ) {
                    listener?.onKeyAction(target.action)
                }

                resetTouchState()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetTouchState() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        activeKey = null
        isSpacebarTrackpad = false
        isGestureDeleting = false
        isGliding = false
        isLongPressPopupOpen = false
        backspaceStepCount = 0
        glidePoints.clear()
        glidePath.reset()
        invalidate()
    }

    private fun findKeyAt(x: Float, y: Float): KeyboardKey? {
        keysWithBounds.firstOrNull { it.contains(x, y) }?.let { return it }
        return keysWithBounds.minByOrNull { kb ->
            hypot((kb.centerX - x).toDouble(), (kb.centerY - y).toDouble())
        }
    }

    private fun showPreviewPopup(kb: KeyboardKey) {
        val tv = previewTextView ?: TextView(context).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(6f).toInt())
        }.also { previewTextView = it }

        val label = kb.character
        val isArabic = label.any { it.code in 0x0600..0x077F }
        tv.typeface = if (isArabic) (customArabicTypeface ?: arabicTypeface) else (customLatinTypeface ?: latinTypeface)
        tv.text = label
        tv.setTextColor(theme.primaryTextColor)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.labelFontSizeSp * 1.45f)
        tv.setBackgroundColor(theme.popupBackgroundColor)

        val popW = (kb.width * 1.32f).roundToInt().coerceAtLeast(dp(44f).roundToInt())
        val popH = (kb.height * 1.38f).roundToInt().coerceAtLeast(dp(56f).roundToInt())

        val pop = previewPopup ?: PopupWindow(tv, popW, popH, false).apply {
            isTouchable = false
            elevation = dp(8f)
        }.also { previewPopup = it }

        pop.width = popW
        pop.height = popH

        val loc = IntArray(2)
        getLocationInWindow(loc)
        val screenW = resources.displayMetrics.widthPixels
        val desiredX = (loc[0] + kb.centerX - popW / 2f).roundToInt().coerceIn(4, (screenW - popW - 4).coerceAtLeast(4))
        val desiredY = (loc[1] + kb.y - popH - dp(4f)).roundToInt()

        if (pop.isShowing) {
            pop.update(desiredX, desiredY, popW, popH)
        } else {
            pop.showAtLocation(this, Gravity.NO_GRAVITY, desiredX, desiredY)
        }
    }

    private fun dismissPreviewPopup() {
        previewPopup?.dismiss()
    }

    private fun showLongPressPopup(kb: KeyboardKey) {
        val items = kb.longPressAlternatives
        if (items.isEmpty()) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = LAYOUT_DIRECTION_LTR // Preserve explicit visual left-to-right slot geometry
            setBackgroundColor(theme.popupBackgroundColor)
            setPadding(dp(4f).roundToInt(), dp(4f).roundToInt(), dp(4f).roundToInt(), dp(4f).roundToInt())
            elevation = dp(12f)
        }

        val itemWidth = dp(46f).roundToInt()
        val itemHeight = dp(52f).roundToInt()
        val centerIdx = kb.longPressCenterIndex.coerceIn(0, items.lastIndex)

        items.forEachIndexed { idx, char ->
            val tv = TextView(context).apply {
                text = char
                gravity = Gravity.CENTER
                val isArabic = char.any { it.code in 0x0600..0x077F }
                typeface = if (isArabic) (customArabicTypeface ?: arabicTypeface) else (customLatinTypeface ?: latinTypeface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(if (idx == centerIdx) theme.accentTextColor else theme.primaryTextColor)
                setBackgroundColor(if (idx == centerIdx) theme.popupHighlightColor else Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(itemWidth, itemHeight)
            }
            container.addView(tv)
        }

        val popW = items.size * itemWidth + dp(8f).roundToInt()
        val popH = itemHeight + dp(8f).roundToInt()
        val pop = PopupWindow(container, popW, popH, false).apply {
            isTouchable = false
            elevation = dp(12f)
        }

        // Exact spatial centering: center slot (longPressCenterIndex) aligns directly over kb.centerX!
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val keyCenterScreenX = loc[0] + kb.centerX
        val centerSlotOffsetX = dp(4f) + centerIdx * itemWidth + itemWidth / 2f
        val screenW = resources.displayMetrics.widthPixels
        val clampedX = (keyCenterScreenX - centerSlotOffsetX).roundToInt()
            .coerceIn(dp(6f).roundToInt(), (screenW - popW - dp(6f).roundToInt()).coerceAtLeast(dp(6f).roundToInt()))
        val desiredY = (loc[1] + kb.y - popH - dp(6f)).roundToInt()

        longPressContainer = container
        longPressPopup = pop
        activePopupSpec = kb to items
        selectedPopupIndex = centerIdx
        isLongPressPopupOpen = true

        pop.showAtLocation(this, Gravity.NO_GRAVITY, clampedX, desiredY)
        container.scaleX = 0.86f
        container.scaleY = 0.86f
        container.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(120L)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        listener?.onPopupFocusTick()
    }

    private fun updateLongPressSelection(rawTouchX: Float) {
        val container = longPressContainer ?: return
        val items = activePopupSpec?.second ?: return
        if (items.isEmpty()) return

        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        val relX = rawTouchX - loc[0] - container.paddingLeft
        val childCount = container.childCount
        if (childCount == 0) return
        val itemW = (container.width - container.paddingLeft - container.paddingRight).toFloat() / childCount
        val idx = (relX / itemW).toInt().coerceIn(0, childCount - 1)

        if (idx != selectedPopupIndex) {
            selectedPopupIndex = idx
            for (i in 0 until childCount) {
                val tv = container.getChildAt(i) as? TextView ?: continue
                val selected = i == idx
                tv.setBackgroundColor(if (selected) theme.popupHighlightColor else Color.TRANSPARENT)
                tv.setTextColor(if (selected) theme.accentTextColor else theme.primaryTextColor)
            }
            listener?.onPopupFocusTick()
        }
    }

    private fun dismissLongPressPopup() {
        longPressPopup?.dismiss()
        longPressPopup = null
        longPressContainer = null
        activePopupSpec = null
        selectedPopupIndex = -1
        isLongPressPopupOpen = false
    }

    fun releasePopups() {
        handler.removeCallbacksAndMessages(null)
        dismissPreviewPopup()
        dismissLongPressPopup()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
