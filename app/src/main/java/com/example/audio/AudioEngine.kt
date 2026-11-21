package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Zero-asset synthesized PCM audio engine for crisp tactile SFX and gentle ambient puzzle music.
 * Safe in headless/Robolectric test environments (gracefully no-ops if AudioTrack is unavailable).
 */
class AudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var musicEnabled: Boolean = true
        private set

    private var musicJob: Job? = null
    private var isAppForeground = true

    private val sampleRate = 22050

    private val tapBuffer by lazy { synthesizeChime(doubleArrayOf(587.33), 0.045, 0.25) }
    private val removeBuffer by lazy { synthesizeChime(doubleArrayOf(523.25, 783.99), 0.14, 0.32) }
    private val errorBuffer by lazy { synthesizeChime(doubleArrayOf(185.0, 146.83), 0.16, 0.32) }
    private val clickBuffer by lazy { synthesizeChime(doubleArrayOf(660.0), 0.035, 0.20) }
    private val hintBuffer by lazy { synthesizeChime(doubleArrayOf(783.99, 1046.50, 1318.51), 0.24, 0.28) }
    private val completeBuffer by lazy {
        synthesizeChime(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 0.45, 0.35)
    }
    private val gameOverBuffer by lazy {
        synthesizeChime(doubleArrayOf(392.0, 349.23, 293.66), 0.36, 0.30)
    }

    fun playButtonClick() {
        if (!soundEnabled) return
        playShortBuffer(clickBuffer)
    }

    fun playArrowTap() {
        if (!soundEnabled) return
        playShortBuffer(tapBuffer)
    }

    fun playArrowRemove() {
        if (!soundEnabled) return
        playShortBuffer(removeBuffer)
    }

    fun playBlockedError() {
        if (!soundEnabled) return
        playShortBuffer(errorBuffer)
    }

    fun playHint() {
        if (!soundEnabled) return
        playShortBuffer(hintBuffer)
    }

    fun playLevelComplete() {
        if (!soundEnabled) return
        playShortBuffer(completeBuffer)
    }

    fun playGameOver() {
        if (!soundEnabled) return
        playShortBuffer(gameOverBuffer)
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        updateMusicPlayback()
    }

    fun onForegroundChanged(inForeground: Boolean) {
        isAppForeground = inForeground
        updateMusicPlayback()
    }

    private fun updateMusicPlayback() {
        if (musicEnabled && isAppForeground) {
            startMusicLoop()
        } else {
            stopMusicLoop()
        }
    }

    private fun startMusicLoop() {
        if (musicJob?.isActive == true) return
        musicJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val loopSamples = synthesizeAmbientLoop()
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
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
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val chunkSize = 2048
                var offset = 0
                while (isActive && musicEnabled && isAppForeground) {
                    val count = minOf(chunkSize, loopSamples.size - offset)
                    val written = track.write(loopSamples, offset, count)
                    if (written < 0) break
                    offset += written
                    if (offset >= loopSamples.size) offset = 0
                }
            } catch (_: Throwable) {
                // Safe fallback on environments without audio hardware
            } finally {
                try {
                    track?.pause()
                    track?.flush()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun stopMusicLoop() {
        musicJob?.cancel()
        musicJob = null
    }

    private fun playShortBuffer(samples: ShortArray) {
        scope.launch {
            var track: AudioTrack? = null
            try {
                val byteSize = samples.size * 2
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L / sampleRate) + 40L
                kotlinx.coroutines.delay(durationMs)
            } catch (_: Throwable) {
                // Ignore audio errors on headless/test devices
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun synthesizeChime(
        freqs: DoubleArray,
        durationSeconds: Double,
        volume: Double
    ): ShortArray {
        val totalSamples = (sampleRate * durationSeconds).toInt().coerceAtLeast(1)
        val buffer = ShortArray(totalSamples)
        val noteLen = totalSamples / freqs.size.coerceAtLeast(1)

        for (i in 0 until totalSamples) {
            val noteIndex = (i / noteLen).coerceAtMost(freqs.size - 1)
            val localI = i - noteIndex * noteLen
            val localT = localI.toDouble() / sampleRate
            val freq = freqs[noteIndex]
            val attack = (localT / 0.008).coerceAtMost(1.0)
            val decay = exp(-localT * 10.0)
            val wave = sin(2.0 * PI * freq * localT) + 0.25 * sin(4.0 * PI * freq * localT)
            val sample = (wave * attack * decay * volume * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    private fun synthesizeAmbientLoop(): ShortArray {
        // Gentle 8-second calming puzzle progression (Cmaj7 - Am7 - Fmaj7 - G6)
        val chords = arrayOf(
            doubleArrayOf(261.63, 329.63, 392.00, 493.88),
            doubleArrayOf(220.00, 261.63, 329.63, 392.00),
            doubleArrayOf(174.61, 261.63, 329.63, 440.00),
            doubleArrayOf(196.00, 293.66, 329.63, 392.00)
        )
        val chordSeconds = 2.0
        val samplesPerChord = (sampleRate * chordSeconds).toInt()
        val total = samplesPerChord * chords.size
        val out = ShortArray(total)

        for (c in chords.indices) {
            val chord = chords[c]
            val baseOffset = c * samplesPerChord
            for (i in 0 until samplesPerChord) {
                val t = i.toDouble() / sampleRate
                val env = sin(PI * (i.toDouble() / samplesPerChord)) // smooth swell
                var sum = 0.0
                for (f in chord) {
                    sum += sin(2.0 * PI * f * t)
                }
                val valScaled = (sum / chord.size) * env * 0.09 * Short.MAX_VALUE
                out[baseOffset + i] = valScaled.toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
        return out
    }
}
