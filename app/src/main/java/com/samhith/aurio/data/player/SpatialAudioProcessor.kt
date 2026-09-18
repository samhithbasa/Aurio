package com.samhith.aurio.data.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Beat-synced "8D / 16D audio": the song travels around the listener's head in time with the music.
 *
 * How it keeps impact instead of just sounding quieter:
 *  - **Bass never moves.** The song is split at ~150 Hz; the low band (kick, bass) stays centred in
 *    both ears at full level, so the punch is never lost to one side. Only mids and highs travel.
 *  - **Motion follows the beat.** Kicks are detected right here in the audio (bass energy jumping
 *    above its recent average). Each beat pushes the sound to its next position with a quick glide,
 *    so it hops around the head on the rhythm. Between beats it keeps drifting slowly, and songs
 *    with no clear kick fall back to a steady orbit.
 *  - **Loudness is held.** Output level is continuously matched to the input, and a soft limiter
 *    catches the peaks, so switching the effect on never makes a song quieter.
 *
 * Position cues on the moving part: level difference between ears, a real-head timing difference
 * (up to 0.66 ms, the cue that places sound outside the head), gentle high-frequency shading on the
 * far ear, and a light room. Real 8D production uses a measured HRTF; this approximates it.
 *
 * 8D  - the vocal/music band orbits around the head, a quarter turn per beat.
 * 16D - two layers: the vocal band orbits one way on the beat, while hi-hats and claps bounce
 *       ear-to-ear on every beat.
 *
 * The processor stays in the chain and passes audio through untouched when the effect is off, so
 * switching modes is instant.
 */
class SpatialAudioProcessor(
    private val settings: SpatialAudioManager
) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var inputChannels = 2

    // --- crossover: 2-pole low-pass per channel, the high band is the exact remainder
    private var bassCoef = 0f
    private var lowL1 = 0f
    private var lowL2 = 0f
    private var lowR1 = 0f
    private var lowR2 = 0f

    // --- 16D split of the moving band into body (vocals) and air (hats, claps)
    private var airCoef = 0f
    private var airState = 0f

    // --- the moving sources
    private lateinit var vocalMover: Mover
    private lateinit var airMover: Mover

    // --- beat detection on the bass band, in 10 ms blocks
    private var beatBlockSize = 441
    private var beatBlockFill = 0
    private var beatBlockEnergy = 0.0
    private var bassAverage = 0.0
    private var samplesSinceBeat = 0L
    private var bounceRight = true

    // --- room
    private lateinit var reverbLeft: FloatArray
    private lateinit var reverbRight: FloatArray
    private var reverbWriteLeft = 0
    private var reverbWriteRight = 0

    // --- loudness matching
    private var levelCoef = 0f
    private var gainCoef = 0f
    private var inputPower = 1e-6f
    private var outputPower = 1e-6f
    private var makeUpGain = 1f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        inputChannels = inputAudioFormat.channelCount

        bassCoef = onePole(CROSSOVER_HZ)
        airCoef = onePole(AIR_SPLIT_HZ)

        val maxDelaySamples = (MAX_ITD_SECONDS * sampleRate).roundToInt() + 3
        vocalMover = Mover(maxDelaySamples, glideCoef(VOCAL_GLIDE_SECONDS), onePole(SHADE_HZ))
        airMover = Mover(maxDelaySamples, glideCoef(AIR_GLIDE_SECONDS), onePole(SHADE_HZ))

        beatBlockSize = (sampleRate / 100).coerceAtLeast(1)

        reverbLeft = FloatArray((REVERB_LEFT_SECONDS * sampleRate).roundToInt() + 1)
        reverbRight = FloatArray((REVERB_RIGHT_SECONDS * sampleRate).roundToInt() + 1)

        levelCoef = (1.0 - exp(-1.0 / (LEVEL_WINDOW_SECONDS * sampleRate))).toFloat()
        gainCoef = (1.0 - exp(-1.0 / (GAIN_SMOOTH_SECONDS * sampleRate))).toFloat()

        resetState()
        // Always stereo out: the effect needs two ears, and mono songs are widened to match
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / (2 * inputChannels)
        if (frames == 0) return

        val output = replaceOutputBuffer(frames * 2 * 2) // frames x stereo x 16-bit
        val mode = settings.mode.value

        if (mode == SpatialMode.OFF) {
            copyThrough(inputBuffer, output, frames)
        } else {
            render(inputBuffer, output, frames, mode)
        }

        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    /** Effect off: pass the song along untouched (upmixing mono so the format stays stable). */
    private fun copyThrough(input: ByteBuffer, output: ByteBuffer, frames: Int) {
        for (i in 0 until frames) {
            val left = input.short
            val right = if (inputChannels > 1) input.short else left
            if (inputChannels > 2) skipExtraChannels(input)
            output.putShort(left)
            output.putShort(right)
        }
    }

    private fun render(input: ByteBuffer, output: ByteBuffer, frames: Int, mode: SpatialMode) {
        val intensity = settings.intensity.value.coerceIn(0f, 1f)
        val isSixteenD = mode == SpatialMode.SIXTEEN_D
        val driftStep = (2.0 * PI / (settings.rotationSeconds.value.coerceAtLeast(1f) * sampleRate)).toFloat()
        val fallbackBeatSamples = (settings.rotationSeconds.value.coerceAtLeast(1f) / 4f * sampleRate).toLong()

        // Nearly ear-to-ear travel at any strength; strength shapes the cues and the room
        val swing = 0.78f + 0.22f * intensity
        val maxDelay = MAX_ITD_SECONDS * sampleRate * (0.75f + 0.25f * intensity)
        val shadeAmount = 0.35f + 0.3f * intensity
        val keepSide = 0.5f - 0.3f * intensity
        val roomMix = (if (isSixteenD) 0.16f else 0.11f) * (0.5f + 0.5f * intensity)

        for (i in 0 until frames) {
            val inL = input.short / 32768f
            val inR = if (inputChannels > 1) input.short / 32768f else inL
            if (inputChannels > 2) skipExtraChannels(input)

            // 1. Split off the bass: it stays centred, at full level, in both ears
            lowL1 += (inL - lowL1) * bassCoef
            lowL2 += (lowL1 - lowL2) * bassCoef
            lowR1 += (inR - lowR1) * bassCoef
            lowR2 += (lowR1 - lowR2) * bassCoef
            val bass = (lowL2 + lowR2) * 0.5f
            val highL = inL - lowL2
            val highR = inR - lowR2
            val mid = (highL + highR) * 0.5f
            val side = (highL - highR) * 0.5f

            // 2. Beat detection on the bass band
            val beat = detectBeat(bass, fallbackBeatSamples)
            if (beat) {
                if (isSixteenD) {
                    vocalMover.target -= QUARTER_TURN * 0.5f // body steps the other way
                    bounceRight = !bounceRight
                    airMover.target = if (bounceRight) HALF_PI else -HALF_PI
                } else {
                    vocalMover.target += QUARTER_TURN
                }
                settings.lastBeatAtNanos = System.nanoTime()
            }

            // 3. Slow drift between beats keeps it moving in songs with sparse kicks
            if (isSixteenD) vocalMover.target -= driftStep else vocalMover.target += driftStep

            // 4. Move the sources
            var movedL: Float
            var movedR: Float
            if (isSixteenD) {
                airState += (mid - airState) * airCoef
                val body = airState
                val air = mid - airState
                vocalMover.render(body, swing, maxDelay, shadeAmount)
                movedL = vocalMover.outL
                movedR = vocalMover.outR
                airMover.render(air, 0.95f, maxDelay, shadeAmount * 0.6f)
                movedL += airMover.outL
                movedR += airMover.outR
            } else {
                vocalMover.render(mid, swing, maxDelay, shadeAmount)
                movedL = vocalMover.outL
                movedR = vocalMover.outR
            }

            // 5. A little room, so it sounds around the listener rather than inside the head
            val roomL = reverbLeft[reverbWriteLeft]
            val roomR = reverbRight[reverbWriteRight]
            reverbLeft[reverbWriteLeft] = movedL + roomR * REVERB_FEEDBACK
            reverbRight[reverbWriteRight] = movedR + roomL * REVERB_FEEDBACK
            reverbWriteLeft = (reverbWriteLeft + 1) % reverbLeft.size
            reverbWriteRight = (reverbWriteRight + 1) % reverbRight.size

            // 6. Reassemble: centred bass, a bit of original width, the moving sound, the room
            var outL = bass + side * keepSide + movedL * (1f - roomMix) + roomL * roomMix
            var outR = bass - side * keepSide + movedR * (1f - roomMix) + roomR * roomMix

            // 7. Hold the song's loudness: match output power to input power, smoothly
            inputPower += ((inL * inL + inR * inR) * 0.5f - inputPower) * levelCoef
            outputPower += ((outL * outL + outR * outR) * 0.5f - outputPower) * levelCoef
            val targetGain = sqrt(inputPower / (outputPower + 1e-9f)).coerceIn(MIN_GAIN, MAX_GAIN)
            makeUpGain += (targetGain - makeUpGain) * gainCoef
            outL *= makeUpGain
            outR *= makeUpGain

            output.putShort(toPcm(softLimit(outL)))
            output.putShort(toPcm(softLimit(outR)))
        }

        vocalMover.wrap()
        airMover.wrap()
        // Shared with the player UI so its animation shows where the sound really is
        settings.liveAngle = vocalMover.angle
        settings.liveAirAngle = airMover.angle
    }

    /**
     * Kick detection: bass energy in a 10 ms block jumping well above its recent average.
     * Same idea as the Music Haptics detector, run here so it lands exactly on the audio.
     */
    private fun detectBeat(bass: Float, fallbackBeatSamples: Long): Boolean {
        samplesSinceBeat++
        beatBlockEnergy += (bass * bass).toDouble()
        beatBlockFill++
        if (beatBlockFill < beatBlockSize) return false

        val rms = sqrt(beatBlockEnergy / beatBlockFill)
        beatBlockEnergy = 0.0
        beatBlockFill = 0

        val minGap = (MIN_BEAT_GAP_SECONDS * sampleRate).toLong()
        val isKick = bassAverage > 0 &&
                rms > bassAverage * BEAT_THRESHOLD &&
                rms > MIN_BEAT_LEVEL &&
                samplesSinceBeat > minGap
        bassAverage = if (bassAverage == 0.0) rms else bassAverage * 0.9 + rms * 0.1

        // No kick for a while (acoustic, ambient, a quiet intro): keep hopping on a steady pulse
        // so the motion never stalls
        val fallbackInterval = fallbackBeatSamples.coerceAtLeast((FALLBACK_MIN_SECONDS * sampleRate).toLong())
        val quietTooLong = samplesSinceBeat > fallbackInterval

        if (isKick || quietTooLong) {
            samplesSinceBeat = 0
            return true
        }
        return false
    }

    private fun resetState() {
        lowL1 = 0f; lowL2 = 0f; lowR1 = 0f; lowR2 = 0f
        airState = 0f
        beatBlockFill = 0
        beatBlockEnergy = 0.0
        bassAverage = 0.0
        samplesSinceBeat = 0
        inputPower = 1e-6f
        outputPower = 1e-6f
        makeUpGain = 1f
        reverbWriteLeft = 0
        reverbWriteRight = 0
        if (::reverbLeft.isInitialized) {
            reverbLeft.fill(0f)
            reverbRight.fill(0f)
        }
        if (::vocalMover.isInitialized) {
            vocalMover.reset()
            airMover.reset()
            airMover.target = HALF_PI
            airMover.angle = HALF_PI
        }
    }

    override fun onFlush() {
        resetState()
    }

    private fun skipExtraChannels(input: ByteBuffer) {
        for (channel in 2 until inputChannels) input.short
    }

    private fun onePole(cutoffHz: Float): Float =
        (1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)).toFloat()

    private fun glideCoef(seconds: Float): Float =
        (1.0 - exp(-1.0 / (seconds * sampleRate))).toFloat()

    /** Transparent below 0.9, then rounds peaks off instead of clipping them. */
    private fun softLimit(x: Float): Float {
        val magnitude = abs(x)
        if (magnitude <= LIMIT_KNEE) return x
        val over = (magnitude - LIMIT_KNEE) / (1f - LIMIT_KNEE)
        val limited = LIMIT_KNEE + (1f - LIMIT_KNEE) * tanh(over)
        return if (x < 0) -limited else limited
    }

    private fun toPcm(value: Float): Short {
        val scaled = (value * 32767f).roundToInt()
        return scaled.coerceIn(-32768, 32767).toShort()
    }

    /**
     * One moving sound source: glides its angle toward [target], and renders a mono signal to
     * two ears with level, timing and tone cues for that angle.
     */
    private class Mover(delaySize: Int, private val glide: Float, private val shadeCoef: Float) {
        var angle = 0f
        var target = 0f
        var outL = 0f
        var outR = 0f

        private val delay = FloatArray(delaySize)
        private var write = 0
        private var shadeL = 0f
        private var shadeR = 0f

        fun render(x: Float, swing: Float, maxDelay: Float, shadeAmount: Float) {
            angle += (target - angle) * glide

            val pan = sin(angle) * swing                 // -1 left .. +1 right
            val depth = (1f - cos(angle)) * 0.5f          // 0 in front .. 1 behind

            // Timing: the far ear hears it a fraction of a millisecond later
            delay[write] = x
            val nearNow = x
            val far = readDelay(abs(pan) * maxDelay)
            write = (write + 1) % delay.size
            val left = if (pan > 0) far else nearNow
            val right = if (pan < 0) far else nearNow

            // Level: equal-power pan, never letting the far ear go completely silent
            val gainL = FAR_FLOOR + (1f - FAR_FLOOR) * sqrt(((1f - pan) * 0.5f).coerceIn(0f, 1f))
            val gainR = FAR_FLOOR + (1f - FAR_FLOOR) * sqrt(((1f + pan) * 0.5f).coerceIn(0f, 1f))

            // Tone: soften only the highs of the far ear (and a touch when behind)
            shadeL += (left - shadeL) * shadeCoef
            shadeR += (right - shadeR) * shadeCoef
            val shadeLeft = (maxOf(pan, 0f) + depth * 0.4f).coerceIn(0f, 1f) * shadeAmount
            val shadeRight = (maxOf(-pan, 0f) + depth * 0.4f).coerceIn(0f, 1f) * shadeAmount
            val tonedL = left - (left - shadeL) * shadeLeft
            val tonedR = right - (right - shadeR) * shadeRight

            val behind = 1f - 0.1f * depth
            outL = tonedL * gainL * behind
            outR = tonedR * gainR * behind
        }

        /** Keeps angles small without changing where the sound is. */
        fun wrap() {
            while (angle > TWO_PI && target > TWO_PI) { angle -= TWO_PI; target -= TWO_PI }
            while (angle < -TWO_PI && target < -TWO_PI) { angle += TWO_PI; target += TWO_PI }
        }

        fun reset() {
            angle = 0f
            target = 0f
            delay.fill(0f)
            write = 0
            shadeL = 0f
            shadeR = 0f
        }

        private fun readDelay(samples: Float): Float {
            val size = delay.size
            val whole = samples.toInt()
            val fraction = samples - whole
            val first = ((write - whole) % size + size) % size
            val second = ((first - 1) % size + size) % size
            return delay[first] * (1f - fraction) + delay[second] * fraction
        }
    }

    private companion object {
        const val TWO_PI = (2 * PI).toFloat()
        const val HALF_PI = (PI / 2).toFloat()
        const val QUARTER_TURN = (PI / 2).toFloat()

        /** Below this the song stays centred: kick and bass keep their punch. */
        const val CROSSOVER_HZ = 150f

        /** 16D: above this is "air" (hats, claps) that bounces ear to ear. */
        const val AIR_SPLIT_HZ = 4000f

        /** Corner of the far-ear high-frequency shading. */
        const val SHADE_HZ = 3000f

        /** How long a beat's hop takes. The air layer snaps faster than the vocals. */
        const val VOCAL_GLIDE_SECONDS = 0.09f
        const val AIR_GLIDE_SECONDS = 0.05f

        /** Largest gap between the ears: the real distance around a human head. */
        const val MAX_ITD_SECONDS = 0.00066f

        /** The far ear never drops below this share of the level, so nothing sounds switched off. */
        const val FAR_FLOOR = 0.12f

        const val BEAT_THRESHOLD = 1.3
        const val MIN_BEAT_LEVEL = 0.01
        const val MIN_BEAT_GAP_SECONDS = 0.11f
        /** Shortest steady hop interval used when a song has no detectable kick. */
        const val FALLBACK_MIN_SECONDS = 1.5f

        const val REVERB_LEFT_SECONDS = 0.037f
        const val REVERB_RIGHT_SECONDS = 0.043f
        const val REVERB_FEEDBACK = 0.28f

        /** Loudness matching: window it measures over, and how gently the gain follows. */
        const val LEVEL_WINDOW_SECONDS = 0.4f
        const val GAIN_SMOOTH_SECONDS = 0.6f
        const val MIN_GAIN = 0.8f
        const val MAX_GAIN = 1.8f

        const val LIMIT_KNEE = 0.9f
    }
}
