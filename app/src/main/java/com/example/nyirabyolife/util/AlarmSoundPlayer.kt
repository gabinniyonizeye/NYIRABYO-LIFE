package com.example.nyirabyolife.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * High-reliability Singleton responsible for playing loud alarm audio and vibrating.
 * Starts directly from TaskAlarmReceiver so sound plays IMMEDIATELY at the scheduled minute,
 * even if the device screen is off, locked, or background activity launch is delayed.
 */
object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Synchronized
    fun startAlarm(context: Context, title: String = "") {
        if (isPlaying) return
        isPlaying = true

        val appContext = context.applicationContext

        // 1. Acquire WakeLock to keep CPU running and screen awakenable
        try {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "nyirabyo:AlarmSoundWakeLock"
            )?.apply {
                acquire(10 * 60 * 1000L) // 10 minutes maximum alarm duration
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Ensure Alarm Volume is Audible
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                if (curVol < (maxVol * 0.6f).toInt()) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        (maxVol * 0.8f).toInt().coerceAtLeast(1),
                        0
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Play Loud Alarm Ringtone in a continuous loop
        try {
            val alertUri: Uri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
                ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI

            mediaPlayer = MediaPlayer().apply {
                setDataSource(appContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Continuous Strong Vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 500, 1000, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun stopAlarm() {
        if (!isPlaying) return
        isPlaying = false

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }
}
