package com.example.core.ime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Differentiates tactile strength and duration across keyboard key categories.
 */
enum class KeyPressHapticType {
    REGULAR,
    SPACE_OR_ENTER,
    BACKSPACE_REPEAT,
    POPUP_TICK
}

/**
 * Provides crisp, low-latency haptic vibration feedback for [SaraikiKeyboardService]
 * using [Vibrator] / [VibratorManager] (API 24+) with [View.performHapticFeedback] fallback.
 */
class KeyboardHapticFeedback(private val context: Context) {

    private val vibrator: Vibrator? by lazy { resolveVibrator(context) }

    @SuppressLint("MissingPermission")
    fun vibrate(
        type: KeyPressHapticType = KeyPressHapticType.REGULAR,
        baseDurationMs: Int = DEFAULT_DURATION_MS,
        enabled: Boolean = true,
        fallbackView: View? = null
    ): Boolean {
        if (!shouldVibrate(enabled, baseDurationMs)) return false

        val durationMs = computeDurationMs(baseDurationMs, type)
        val amplitude = computeAmplitude(type)

        val vib = vibrator
        if (vib != null && vib.hasVibrator()) {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(durationMs)
                }
                true
            } catch (_: SecurityException) {
                performViewFallback(fallbackView)
            } catch (_: RuntimeException) {
                performViewFallback(fallbackView)
            }
        }
        return performViewFallback(fallbackView)
    }

    private fun performViewFallback(view: View?): Boolean {
        if (view == null) return false
        return try {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS = 18

        fun shouldVibrate(enabled: Boolean, baseDurationMs: Int): Boolean =
            enabled && baseDurationMs > 0

        fun computeDurationMs(baseDurationMs: Int, type: KeyPressHapticType): Long {
            val safeBase = baseDurationMs.coerceAtLeast(1)
            val scaled = when (type) {
                KeyPressHapticType.REGULAR -> safeBase.toLong()
                KeyPressHapticType.SPACE_OR_ENTER -> (safeBase * 1.35f).toLong().coerceAtMost(55L)
                KeyPressHapticType.BACKSPACE_REPEAT -> (safeBase * 0.65f).toLong().coerceAtLeast(1L)
                KeyPressHapticType.POPUP_TICK -> (safeBase * 0.55f).toLong().coerceAtLeast(1L)
            }
            return scaled.coerceIn(1L, 60L)
        }

        fun computeAmplitude(type: KeyPressHapticType): Int = when (type) {
            KeyPressHapticType.REGULAR -> 140
            KeyPressHapticType.SPACE_OR_ENTER -> 210
            KeyPressHapticType.BACKSPACE_REPEAT -> 100
            KeyPressHapticType.POPUP_TICK -> 90
        }

        fun resolveHapticType(action: KeyAction): KeyPressHapticType? = when (action) {
            KeyAction.Spacer -> null
            KeyAction.Space, KeyAction.Enter, KeyAction.GlobeSwitch -> KeyPressHapticType.SPACE_OR_ENTER
            KeyAction.Backspace -> KeyPressHapticType.BACKSPACE_REPEAT
            is KeyAction.CommitChar,
            KeyAction.Shift,
            KeyAction.ToggleSymbols,
            KeyAction.ToggleMoreSymbols,
            KeyAction.Comma,
            KeyAction.Period,
            KeyAction.EmojiDrawer -> KeyPressHapticType.REGULAR
        }
    }
}
