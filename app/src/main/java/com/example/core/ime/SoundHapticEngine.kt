package com.example.core.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.core.preferences.KeyboardSettings
import com.example.core.preferences.SoundProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class HapticKind { REGULAR, SPACE_OR_ENTER, BACKSPACE_REPEAT, POPUP_TICK }

class SoundHapticEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var soundPool: SoundPool? = null
    private val soundIds = HashMap<SoundProfile, Int>()
    private val vibrator: Vibrator? = initVibrator()

    @Volatile
    private var initialized = false

    private fun initVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun initializeAsync() {
        if (initialized) return
        initialized = true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
        soundPool = pool

        scope.launch {
            SoundProfile.entries.forEach { profile ->
                val wavFile = File(context.cacheDir, "kbd_${profile.id}.wav")
                if (!wavFile.exists()) {
                    writeSynthesizedWav(wavFile, profile)
                }
                val id = pool.load(wavFile.absolutePath, 1)
                soundIds[profile] = id
            }
        }
    }

    fun playKeyFeedback(settings: KeyboardSettings, hapticKind: HapticKind) {
        if (settings.soundEnabled && settings.soundVolume > 0.01f) {
            playSound(settings.soundProfile, settings.soundVolume, hapticKind)
        }
        if (settings.hapticEnabled && settings.hapticDurationMs > 0) {
            vibrate(settings.hapticDurationMs, hapticKind)
        }
    }

    fun previewSound(profile: SoundProfile, volume: Float) {
        if (!initialized) initializeAsync()
        playSound(profile, volume, HapticKind.REGULAR)
    }

    fun previewHaptic(durationMs: Int) {
        vibrate(durationMs, HapticKind.REGULAR)
    }

    private fun playSound(profile: SoundProfile, volume: Float, kind: HapticKind) {
        val pool = soundPool ?: return
        val id = soundIds[profile] ?: return
        val rate = when (kind) {
            HapticKind.REGULAR -> 1.0f
            HapticKind.SPACE_OR_ENTER -> 0.88f
            HapticKind.BACKSPACE_REPEAT -> 1.12f
            HapticKind.POPUP_TICK -> 1.24f
        }
        val v = volume.coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, rate)
    }

    private fun vibrate(baseDurationMs: Int, kind: HapticKind) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val duration = when (kind) {
            HapticKind.REGULAR -> baseDurationMs.toLong()
            HapticKind.SPACE_OR_ENTER -> (baseDurationMs * 1.35f).toLong().coerceAtMost(55L)
            HapticKind.BACKSPACE_REPEAT -> (baseDurationMs * 0.65f).toLong().coerceAtLeast(1L)
            HapticKind.POPUP_TICK -> (baseDurationMs * 0.55f).toLong().coerceAtLeast(1L)
        }.coerceIn(1L, 60L)

        val amplitude = when (kind) {
            HapticKind.REGULAR -> 140
            HapticKind.SPACE_OR_ENTER -> 210
            HapticKind.BACKSPACE_REPEAT -> 100
            HapticKind.POPUP_TICK -> 90
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(duration, amplitude)
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(duration)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        initialized = false
    }

    private fun writeSynthesizedWav(file: File, profile: SoundProfile) {
        val sampleRate = 22050
        val durationSec = when (profile) {
            SoundProfile.SOFT_CLICK -> 0.018
            SoundProfile.MECHANICAL_TACTILE -> 0.026
            SoundProfile.TYPEWRITER -> 0.032
            SoundProfile.WATERDROP -> 0.036
        }
        val numSamples = (sampleRate * durationSec).toInt()
        val pcm = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t / (durationSec * 0.28))
            val wave = when (profile) {
                SoundProfile.SOFT_CLICK -> {
                    sin(2 * PI * 1150.0 * t) * 0.6 + sin(2 * PI * 420.0 * t) * 0.4
                }
                SoundProfile.MECHANICAL_TACTILE -> {
                    val click = if (t < 0.004) sin(2 * PI * 2400.0 * t) else 0.0
                    click * 0.7 + sin(2 * PI * 680.0 * t) * 0.5
                }
                SoundProfile.TYPEWRITER -> {
                    val strike = sin(2 * PI * 1850.0 * t) * 0.55 + sin(2 * PI * 920.0 * t) * 0.45
                    strike * if (t < 0.008) 1.0 else 0.4
                }
                SoundProfile.WATERDROP -> {
                    val sweepFreq = 540.0 + (t / durationSec) * 900.0
                    sin(2 * PI * sweepFreq * t) * 0.85
                }
            }
            pcm[i] = (wave * env * Short.MAX_VALUE * 0.65).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val dataSize = numSamples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }.array()

        FileOutputStream(file).use { out ->
            out.write(header)
            val dataBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { dataBuf.putShort(it) }
            out.write(dataBuf.array())
        }
    }
}
