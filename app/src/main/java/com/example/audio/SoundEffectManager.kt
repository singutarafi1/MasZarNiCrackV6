package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

class SoundEffectManager {

    private val audioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FastDeckAudioThread").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + audioExecutor.asCoroutineDispatcher())

    @Volatile
    var isEnabled: Boolean = true

    /**
     * Crisp ascending double-blip when an action starts.
     */
    fun playActionStart() {
        if (!isEnabled) return
        playSequence(
            listOf(
                ToneStep(freqHz = 740.0, durationMs = 45, volume = 0.45f),
                ToneStep(freqHz = 1108.73, durationMs = 60, volume = 0.50f)
            )
        )
    }

    /**
     * Distinct descending confirmation/stop tone when an action stops or completes.
     */
    fun playActionStop() {
        if (!isEnabled) return
        playSequence(
            listOf(
                ToneStep(freqHz = 783.99, durationMs = 65, volume = 0.45f),
                ToneStep(freqHz = 493.88, durationMs = 115, volume = 0.50f)
            )
        )
    }

    /**
     * Bright 3-note ascending chime when a USB OTG port attaches.
     */
    fun playPortAttached() {
        if (!isEnabled) return
        playSequence(
            listOf(
                ToneStep(freqHz = 523.25, durationMs = 55, volume = 0.48f),
                ToneStep(freqHz = 659.25, durationMs = 55, volume = 0.48f),
                ToneStep(freqHz = 1046.50, durationMs = 110, volume = 0.55f)
            )
        )
    }

    /**
     * Warm 2-note descending chime when a USB OTG port detaches.
     */
    fun playPortDetached() {
        if (!isEnabled) return
        playSequence(
            listOf(
                ToneStep(freqHz = 659.25, durationMs = 70, volume = 0.48f),
                ToneStep(freqHz = 349.23, durationMs = 135, volume = 0.52f)
            )
        )
    }

    private data class ToneStep(
        val freqHz: Double,
        val durationMs: Int,
        val volume: Float
    )

    private fun playSequence(steps: List<ToneStep>) {
        scope.launch {
            runCatching {
                val sampleRate = 22050
                val totalSamples = steps.sumOf { (sampleRate * it.durationMs) / 1000 }
                if (totalSamples <= 0) return@runCatching

                val pcm = ShortArray(totalSamples)
                var cursor = 0

                for (step in steps) {
                    val count = (sampleRate * step.durationMs) / 1000
                    val fadeSamples = (count / 6).coerceAtLeast(1)
                    for (i in 0 until count) {
                        val env = when {
                            i < fadeSamples -> i.toFloat() / fadeSamples.toFloat()
                            i > count - fadeSamples -> (count - i).toFloat() / fadeSamples.toFloat()
                            else -> 1f
                        }
                        val angle = 2.0 * PI * step.freqHz * i / sampleRate
                        val sample = (sin(angle) * Short.MAX_VALUE * step.volume * env).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                        pcm[cursor++] = sample
                    }
                }

                val bufferBytes = pcm.size * 2
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return@runCatching

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(maxOf(bufferBytes, minBuf))
                    .build()

                try {
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    val totalMs = steps.sumOf { it.durationMs }.toLong() + 30L
                    Thread.sleep(totalMs)
                } finally {
                    runCatching { track.stop() }
                    runCatching { track.release() }
                }
            }
        }
    }
}
