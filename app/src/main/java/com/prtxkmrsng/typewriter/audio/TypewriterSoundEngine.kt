package com.prtxkmrsng.typewriter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class TypewriterSoundEngine {
    private val sampleRate = 44100
    @Volatile
    var isMuted: Boolean = false

    // Pre-synthesized PCM buffers
    private val keyStrikeBuffers = List(4) { generateKeyStrike(it) }
    private val spacebarBuffer = generateSpacebarThud()
    private val carriageReturnBuffer = generateCarriageReturn()
    private val bellBuffer = generateMarginBell()
    private val platenTickBuffer = generatePlatenTick()

    // Pre-warmed round-robin AudioTrack voice pools (zero allocation during typing)
    private val keyVoicePool: List<AudioTrack> = List(8) { idx ->
        createStaticTrack(keyStrikeBuffers[idx % keyStrikeBuffers.size])
    }.filterNotNull()
    private val keyVoiceCursor = AtomicInteger(0)

    private val spaceVoicePool: List<AudioTrack> = List(3) {
        createStaticTrack(spacebarBuffer)
    }.filterNotNull()
    private val spaceVoiceCursor = AtomicInteger(0)

    private val crTrack: AudioTrack? = createStaticTrack(carriageReturnBuffer)
    private val bellTrack: AudioTrack? = createStaticTrack(bellBuffer)
    private val platenTickTrack: AudioTrack? = createStaticTrack(platenTickBuffer)

    fun playKeyStrike() {
        if (isMuted || keyVoicePool.isEmpty()) return
        val index = (keyVoiceCursor.getAndIncrement() and Int.MAX_VALUE) % keyVoicePool.size
        retriggerStaticTrack(keyVoicePool[index])
    }

    fun playSpacebar() {
        if (isMuted || spaceVoicePool.isEmpty()) return
        val index = (spaceVoiceCursor.getAndIncrement() and Int.MAX_VALUE) % spaceVoicePool.size
        retriggerStaticTrack(spaceVoicePool[index])
    }

    fun playCarriageReturn() {
        if (isMuted) return
        retriggerStaticTrack(crTrack)
    }

    fun playMarginBell() {
        if (isMuted) return
        retriggerStaticTrack(bellTrack)
    }

    fun playPlatenTick() {
        if (isMuted) return
        retriggerStaticTrack(platenTickTrack)
    }

    private fun createStaticTrack(pcmData: ShortArray): AudioTrack? {
        return runCatching {
            AudioTrack.Builder()
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
                .apply {
                    write(pcmData, 0, pcmData.size)
                }
        }.getOrNull()
    }

    @Synchronized
    private fun retriggerStaticTrack(track: AudioTrack?) {
        if (track == null) return
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
        } catch (_: Exception) {
        }
    }

    fun release() {
        (keyVoicePool + spaceVoicePool + listOfNotNull(crTrack, bellTrack, platenTickTrack)).forEach { track ->
            runCatching {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                }
                track.release()
            }
        }
    }

    private fun generateKeyStrike(variant: Int): ShortArray {
        val durationSec = 0.048
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val pitchOffset = 1.0 + (variant - 1.5) * 0.055
        val rnd = Random(variant * 100 + 7)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val clickEnv = exp(-t * 285.0)
            val t2 = (t - 0.015).coerceAtLeast(0.0)
            val tickEnv = if (t >= 0.015) exp(-t2 * 360.0) * 0.45 else 0.0

            val noise = (rnd.nextDouble() * 2.0 - 1.0)
            val metallicResonance =
                sin(2.0 * PI * 1880.0 * pitchOffset * t) * 0.5 +
                    sin(2.0 * PI * 3420.0 * pitchOffset * t) * 0.3 +
                    sin(2.0 * PI * 430.0 * pitchOffset * t) * 0.6

            val signal = ((noise * 0.65 + metallicResonance * 0.35) * clickEnv) +
                ((noise * 0.7 + sin(2.0 * PI * 1250.0 * t2) * 0.3) * tickEnv)

            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return samples
    }

    private fun generateSpacebarThud(): ShortArray {
        val durationSec = 0.055
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val rnd = Random(42)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 195.0)
            val noise = (rnd.nextDouble() * 2.0 - 1.0)
            val lowBody = sin(2.0 * PI * 185.0 * t) * 0.72 + sin(2.0 * PI * 355.0 * t) * 0.28
            val signal = (noise * 0.42 + lowBody * 0.58) * env
            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
        return samples
    }

    private fun generateCarriageReturn(): ShortArray {
        val durationSec = 0.26
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val rnd = Random(99)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val ratchetMod = sin(2.0 * PI * 38.0 * t).coerceAtLeast(0.0)
            val zipEnv = if (t < 0.20) (1.0 - t / 0.22) * ratchetMod else 0.0
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

    private fun generateMarginBell(): ShortArray {
        val durationSec = 0.55
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 7.5)
            val strikeClick = exp(-t * 400.0) * sin(2.0 * PI * 4200.0 * t) * 0.3
            val bell = sin(2.0 * PI * 2480.0 * t) * 0.65 +
                sin(2.0 * PI * 4960.0 * t) * 0.25 +
                sin(2.0 * PI * 7120.0 * t) * 0.10

            val signal = (bell * env) + strikeClick
            samples[i] = (signal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.75).toInt().toShort()
        }
        return samples
    }

    private fun generatePlatenTick(): ShortArray {
        val durationSec = 0.028
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val rnd = Random(77)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 320.0)
            val noise = (rnd.nextDouble() * 2.0 - 1.0)
            val click = sin(2.0 * PI * 780.0 * t) * 0.6 + noise * 0.4
            samples[i] = (click * env * Short.MAX_VALUE * 0.55).toInt().toShort()
        }
        return samples
    }
}
