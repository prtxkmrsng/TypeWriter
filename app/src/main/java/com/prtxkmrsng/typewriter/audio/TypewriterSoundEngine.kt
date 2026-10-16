package com.prtxkmrsng.typewriter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class TypewriterSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 44100
    var isMuted: Boolean = false

    // Pre-synthesized PCM buffers for zero-latency playback
    private val keyStrikeBuffers = List(4) { generateKeyStrike(it) }
    private val spacebarBuffer = generateSpacebarThud()
    private val carriageReturnBuffer = generateCarriageReturn()
    private val bellBuffer = generateMarginBell()

    fun playKeyStrike() {
        if (isMuted) return
        val buffer = keyStrikeBuffers.random()
        playPcm(buffer)
    }

    fun playSpacebar() {
        if (isMuted) return
        playPcm(spacebarBuffer)
    }

    fun playCarriageReturn() {
        if (isMuted) return
        playPcm(carriageReturnBuffer)
    }

    fun playMarginBell() {
        if (isMuted) return
        playPcm(bellBuffer)
    }

    private fun playPcm(pcmData: ShortArray) {
        scope.launch {
            try {
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
                    .setBufferSizeInBytes(pcmData.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcmData, 0, pcmData.size)
                track.play()
                // Release after playback finishes
                val durationMs = (pcmData.size * 1000L) / sampleRate + 50L
                kotlinx.coroutines.delay(durationMs)
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Synthesizes a sharp metallic typebar impact + platen thud (~45ms)
     */
    private fun generateKeyStrike(variant: Int): ShortArray {
        val durationSec = 0.048
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val pitchOffset = 1.0 + (variant - 1.5) * 0.06
        val rnd = Random(variant * 100)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Sharp metallic click envelope
            val clickEnv = exp(-t * 280.0)
            // Secondary mechanical escapement tick (~18ms later)
            val t2 = (t - 0.016).coerceAtLeast(0.0)
            val tickEnv = if (t >= 0.016) exp(-t2 * 350.0) * 0.45 else 0.0

            val noise = (rnd.nextDouble() * 2.0 - 1.0)
            val metallicResonance =
                sin(2.0 * PI * 1850.0 * pitchOffset * t) * 0.5 +
                sin(2.0 * PI * 3400.0 * pitchOffset * t) * 0.3 +
                sin(2.0 * PI * 420.0 * pitchOffset * t) * 0.6

            val signal = ((noise * 0.65 + metallicResonance * 0.35) * clickEnv) +
                ((noise * 0.7 + sin(2.0 * PI * 1200.0 * t2) * 0.3) * tickEnv)

            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return samples
    }

    /**
     * Synthesizes a deeper, hollower wooden/metal frame thud for the spacebar (~55ms)
     */
    private fun generateSpacebarThud(): ShortArray {
        val durationSec = 0.055
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val rnd = Random(42)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 190.0)
            val noise = (rnd.nextDouble() * 2.0 - 1.0)
            val lowBody = sin(2.0 * PI * 190.0 * t) * 0.7 + sin(2.0 * PI * 360.0 * t) * 0.3
            val signal = (noise * 0.45 + lowBody * 0.55) * env
            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return samples
    }

    /**
     * Synthesizes the ratchet platen roll + carriage slide zip + final stop clunk (~260ms)
     */
    private fun generateCarriageReturn(): ShortArray {
        val durationSec = 0.26
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val rnd = Random(99)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // 3 distinct ratchet clicks as paper rolls up in the first 90ms
            val ratchetMod = sin(2.0 * PI * 38.0 * t).coerceAtLeast(0.0)
            val zipEnv = if (t < 0.20) (1.0 - t / 0.22) * ratchetMod else 0.0
            // Final carriage stop thud at t = 0.20s
            val stopT = (t - 0.20).coerceAtLeast(0.0)
            val stopEnv = if (t >= 0.20) exp(-stopT * 140.0) else 0.0

            val noise = (rnd.nextDouble() * 2.0 - 1.0)
            val ratchetSound = (noise * 0.6 + sin(2.0 * PI * 950.0 * t) * 0.4) * zipEnv * 0.65
            val stopClunk = (noise * 0.5 + sin(2.0 * PI * 240.0 * stopT) * 0.5) * stopEnv * 0.95

            val signal = ratchetSound + stopClunk
            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return samples
    }

    /**
     * Synthesizes a bright brass typewriter margin bell (~550ms)
     */
    private fun generateMarginBell(): ShortArray {
        val durationSec = 0.55
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 7.5)
            val strikeClick = exp(-t * 400.0) * sin(2.0 * PI * 4200.0 * t) * 0.3
            // Brass bell harmonics
            val bell = sin(2.0 * PI * 2480.0 * t) * 0.65 +
                sin(2.0 * PI * 4960.0 * t) * 0.25 +
                sin(2.0 * PI * 7120.0 * t) * 0.10

            val signal = (bell * env) + strikeClick
            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.75).toInt().toShort()
        }
        return samples
    }
}
