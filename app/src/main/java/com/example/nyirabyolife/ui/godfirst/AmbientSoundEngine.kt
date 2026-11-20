package com.example.nyirabyolife.ui.godfirst

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * AmbientSoundEngine: Synthesizes continuous, tranquil background soundscapes in real-time.
 *
 * Soundscapes:
 * 1. CELESTIAL_432HZ: A warm multi-harmonic Tibetan singing bowl drone with a 10s breathing LFO wave.
 * 2. GENTLE_RAIN: A soothing, natural rainfall soundscape generated using Paul Kellet's Pink Noise algorithm.
 * 3. OFF: Silent background.
 *
 * Operates with 0 MP3 files, 0 network bandwidth, and minimal battery overhead.
 */
class AmbientSoundEngine {

    enum class AmbientMode(val label: String, val shortLabel: String) {
        CELESTIAL_432HZ("🎶 432 Hz Drone 🕊️", "432 Hz"),
        GENTLE_RAIN("🌧️ Gentle Rain", "Rain"),
        OFF("🔇 Mute Background", "Mute")
    }

    private var currentMode = AmbientMode.CELESTIAL_432HZ
    @Volatile private var isRunning = false
    private var workerThread: Thread? = null
    private var audioTrack: AudioTrack? = null
    private val volume = 0.14f // Soothing background volume level

    fun getMode(): AmbientMode = currentMode

    fun toggleNextMode(): AmbientMode {
        currentMode = when (currentMode) {
            AmbientMode.CELESTIAL_432HZ -> AmbientMode.GENTLE_RAIN
            AmbientMode.GENTLE_RAIN -> AmbientMode.OFF
            AmbientMode.OFF -> AmbientMode.CELESTIAL_432HZ
        }
        return currentMode
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        workerThread = Thread {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 4)

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
                val chunk = ShortArray(1024)
                var sampleIndex = 0L

                // Pink noise filter state variables (Paul Kellet algorithm)
                var b0 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                var b3 = 0.0
                var b4 = 0.0
                var b5 = 0.0
                var b6 = 0.0

                while (isRunning) {
                    if (currentMode == AmbientMode.OFF) {
                        // When muted, feed silence with slight sleep to conserve CPU
                        chunk.fill(0)
                        audioTrack?.write(chunk, 0, chunk.size)
                        Thread.sleep(20)
                        continue
                    }

                    for (i in chunk.indices) {
                        val t = (sampleIndex + i).toDouble() / sampleRate
                        val rawSample: Double = when (currentMode) {
                            AmbientMode.CELESTIAL_432HZ -> {
                                // 10-second breathing LFO wave (0.1 Hz) matching natural human resting breath
                                val lfo = 0.72 + 0.28 * sin(2.0 * PI * 0.10 * t)
                                // Multi-harmonic healing cord: 108Hz sub-bass root, 216Hz octave, 324Hz fifth, 432Hz harmonic
                                val root = sin(2.0 * PI * 108.0 * t) * 0.40
                                val mid = sin(2.0 * PI * 216.0 * t + 0.3) * 0.30
                                val fifth = sin(2.0 * PI * 324.0 * t + 0.6) * 0.15
                                val crown = sin(2.0 * PI * 432.0 * t + 1.1) * 0.15
                                (root + mid + fifth + crown) * lfo
                            }
                            AmbientMode.GENTLE_RAIN -> {
                                // Paul Kellet Pink Noise algorithm for soothing, natural rain sound
                                val white = Math.random() * 2.0 - 1.0
                                b0 = 0.99886 * b0 + white * 0.0555179
                                b1 = 0.99332 * b1 + white * 0.0750759
                                b2 = 0.96900 * b2 + white * 0.1538520
                                b3 = 0.86650 * b3 + white * 0.3104856
                                b4 = 0.55000 * b4 + white * 0.5329522
                                b5 = -0.7616 * b5 - white * 0.0168980
                                val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                                b6 = white * 0.115926
                                // Gentle ambient wave swell
                                val waveMod = 0.85 + 0.15 * sin(2.0 * PI * 0.07 * t)
                                pink * waveMod
                            }
                            AmbientMode.OFF -> 0.0
                        }

                        val sampleShort = (rawSample * volume * Short.MAX_VALUE)
                            .toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                        chunk[i] = sampleShort
                    }

                    audioTrack?.write(chunk, 0, chunk.size)
                    sampleIndex += chunk.size
                }
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
                audioTrack = null
            }
        }
        workerThread?.isDaemon = true
        workerThread?.start()
    }

    fun pause() {
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        if (!isRunning) {
            start()
        } else {
            try {
                audioTrack?.play()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        workerThread = null
    }
}
