package com.prtxkmrsng.typewriter.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class TypewriterSoundEngine(private val context: Context) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val sampleRate = 44100
    private var isMuted = false

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        return isMuted
    }

    fun isMuted(): Boolean = isMuted

    fun playKeystroke() {
        if (isMuted) return
        executor.execute {
            generateAndPlayKeystroke()
        }
    }

    fun playReturn() {
        if (isMuted) return
        executor.execute {
            generateAndPlayBell()
        }
    }

    fun playBackspace() {
        if (isMuted) return
        executor.execute {
            generateAndPlayBackspace()
        }
    }

    private fun generateAndPlayKeystroke() {
        val durationMs = 45
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        val frequency = 120.0 + Random.nextDouble(-15.0, 15.0)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = (1.0 - (i.toDouble() / numSamples))
            val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.45
            val tone = sin(2.0 * PI * frequency * t) * 0.55
            val sample = ((noise + tone) * decay * Short.MAX_VALUE * 0.65).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawAudio(buffer)
    }

    private fun generateAndPlayBackspace() {
        val durationMs = 35
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val decay = (1.0 - (i.toDouble() / numSamples))
            val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.6
            val sample = (noise * decay * Short.MAX_VALUE * 0.45).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawAudio(buffer)
    }

    private fun generateAndPlayBell() {
        val durationMs = 280
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        val f1 = 1760.0
        val f2 = 2640.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = kotlin.math.exp(-12.0 * (i.toDouble() / numSamples))
            val bellTone = (sin(2.0 * PI * f1 * t) * 0.7 + sin(2.0 * PI * f2 * t) * 0.3)
            val sample = (bellTone * decay * Short.MAX_VALUE * 0.6).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        playRawAudio(buffer)
    }

    private fun playRawAudio(buffer: ShortArray) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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
            .setBufferSizeInBytes(maxOf(minBufferSize, buffer.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        try {
            Thread.sleep((buffer.size * 1000L) / sampleRate + 20)
        } catch (_: InterruptedException) {}
        track.stop()
        track.release()
    }

    fun release() {
        executor.shutdown()
    }
}
