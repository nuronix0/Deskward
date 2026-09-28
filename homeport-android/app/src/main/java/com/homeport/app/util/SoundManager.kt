package com.homeport.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.homeport.app.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Premium Apple/visionOS-inspired UI Sound & Haptics Engine.
 * Loads lightweight, tactile sound effects from uisfx into SoundPool
 * for zero-latency, high-fidelity micro-interactions.
 */
class SoundManager private constructor(private val appContext: Context) {

    private val tag = "SoundManager"
    private var soundPool: SoundPool? = null
    private val soundMap = ConcurrentHashMap<SoundEffect, Int>()
    private val loadedSounds = ConcurrentHashMap.newKeySet<Int>()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    enum class SoundEffect {
        CONNECT,
        DISCONNECT,
        TRANSFER_START,
        SUCCESS,
        EXPAND,
        COLLAPSE,
        TAP,
        NOTIFICATION
    }

    init {
        initSoundPool()
    }

    private val pendingPlays = ConcurrentHashMap<Int, () -> Unit>()

    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSounds.add(sampleId)
                    pendingPlays.remove(sampleId)?.invoke()
                } else {
                    Log.w(tag, "Failed to load sound sample $sampleId, status: $status")
                }
            }

            soundMap[SoundEffect.CONNECT] = pool.load(appContext, R.raw.sound_connect, 1)
            soundMap[SoundEffect.DISCONNECT] = pool.load(appContext, R.raw.sound_disconnect, 1)
            soundMap[SoundEffect.TRANSFER_START] = pool.load(appContext, R.raw.sound_transfer_start, 1)
            soundMap[SoundEffect.SUCCESS] = pool.load(appContext, R.raw.sound_success, 1)
            soundMap[SoundEffect.EXPAND] = pool.load(appContext, R.raw.sound_expand, 1)
            soundMap[SoundEffect.COLLAPSE] = pool.load(appContext, R.raw.sound_collapse, 1)
            soundMap[SoundEffect.TAP] = pool.load(appContext, R.raw.sound_tap, 1)
            soundMap[SoundEffect.NOTIFICATION] = pool.load(appContext, R.raw.sound_notification, 1)

            soundPool = pool
        } catch (e: Exception) {
            Log.e(tag, "Error initializing SoundPool", e)
        }
    }

    fun play(effect: SoundEffect, volume: Float = 0.85f, withHaptic: Boolean = true) {
        val pool = soundPool ?: return
        val soundId = soundMap[effect] ?: return

        if (loadedSounds.contains(soundId)) {
            pool.play(soundId, volume, volume, 1, 0, 1.0f)
        } else {
            pendingPlays[soundId] = { pool.play(soundId, volume, volume, 1, 0, 1.0f) }
        }

        if (withHaptic) {
            performTactileHaptic(effect)
        }
    }

    fun playConnect() = play(SoundEffect.CONNECT, volume = 0.9f)
    fun playDisconnect() = play(SoundEffect.DISCONNECT, volume = 0.8f)
    fun playTransferStart() = play(SoundEffect.TRANSFER_START, volume = 0.85f)
    fun playSuccess() = play(SoundEffect.SUCCESS, volume = 0.95f)
    fun playExpand() = play(SoundEffect.EXPAND, volume = 0.75f)
    fun playCollapse() = play(SoundEffect.COLLAPSE, volume = 0.7f)
    fun playTap() = play(SoundEffect.TAP, volume = 0.5f)
    fun playNotification() = play(SoundEffect.NOTIFICATION, volume = 0.85f)

    private fun performTactileHaptic(effect: SoundEffect) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrationEffect = when (effect) {
                    SoundEffect.CONNECT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    SoundEffect.DISCONNECT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    SoundEffect.TRANSFER_START -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    SoundEffect.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    SoundEffect.EXPAND -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    SoundEffect.COLLAPSE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    SoundEffect.TAP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    SoundEffect.NOTIFICATION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                }
                vib.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(25)
            }
        } catch (e: Exception) {
            // Ignore haptic exceptions on restricted hardware
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loadedSounds.clear()
        soundMap.clear()
    }

    companion object {
        @Volatile
        private var instance: SoundManager? = null

        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
