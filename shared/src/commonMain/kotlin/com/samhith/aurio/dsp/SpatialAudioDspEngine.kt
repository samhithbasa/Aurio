package com.samhith.aurio.dsp

import com.samhith.aurio.model.SpatialMode
import com.samhith.aurio.model.SpatialTelemetry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure Kotlin Multiplatform 8D & 16D Spatial Audio DSP Engine.
 * Runs identically on Android (ExoPlayer AudioProcessor) and iOS (AVAudioEngine / CoreAudio tap).
 */
class SpatialAudioDspEngine(
    var sampleRate: Int
) {
    constructor() : this(44100)
    // --- Linkwitz-Riley 2nd-order crossover for phase-aligned bass separation (160 Hz)
    private var lpL1 = 0f; private var lpL2 = 0f
    private var lpR1 = 0f; private var lpR2 = 0f
    private var crossoverCoef = 0f

    // --- 16D Multi-Band Split: Vocal/Body (< 3.6 kHz) vs Air/Perception (> 3.6 kHz)
    private var airSplitCoef = 0f
    private var airL1 = 0f; private var airR1 = 0f

    // --- Spatial Movers (Vocal/Melody, Counter-Traveling Beat, and High Air Layer)
    private lateinit var vocalMover: ContinuousSpatialMover
    private lateinit var beatMover: ContinuousSpatialMover
    private lateinit var airMover: ContinuousSpatialMover

    // --- Dynamic Beat & Kick Punch Engine
    private var beatBlockSize = 441
    private var beatBlockFill = 0
    private var beatBlockEnergy = 0.0
    private var bassAverageRms = 0.0
    private var samplesSinceBeat = 0L
    private var dynamicBassPunch = 0f

    // --- Early Reflection Acoustic Space
    private lateinit var earlyReflLeft: FloatArray
    private lateinit var earlyReflRight: FloatArray
    private var reflWriteLeft = 0
    private var reflWriteRight = 0

    // --- Constant-Loudness Normalizer
    private var inputEnergy = 1e-5f
    private var outputEnergy = 1e-5f
    private var makeUpGain = 1.15f
    private var energySmoothCoef = 0f
    private var gainSmoothCoef = 0f

    // --- Live Telemetry for Visualizer
    var liveVocalAngle: Float = 0f; private set
    var liveBeatAngle: Float = PI.toFloat(); private set
    var liveAirAngle: Float = (PI / 2).toFloat(); private set
    var lastBeatAtNanos: Long = 0L; private set

    init {
        configure(sampleRate)
    }

    fun configure(rate: Int) {
        sampleRate = rate
        crossoverCoef = onePole(CROSSOVER_HZ)
        airSplitCoef = onePole(AIR_SPLIT_HZ)

        val maxDelaySamples = (MAX_ITD_SECONDS * sampleRate).roundToInt() + 8
        vocalMover = ContinuousSpatialMover(maxDelaySamples, onePole(PINNA_SHADE_HZ))
        beatMover = ContinuousSpatialMover(maxDelaySamples, onePole(PINNA_SHADE_HZ))
        airMover = ContinuousSpatialMover(maxDelaySamples, onePole(PINNA_SHADE_HZ))

        beatBlockSize = (sampleRate / 100).coerceAtLeast(1)

        earlyReflLeft = FloatArray((REFL_LEFT_SECONDS * sampleRate).roundToInt() + 1)
        earlyReflRight = FloatArray((REFL_RIGHT_SECONDS * sampleRate).roundToInt() + 1)

        energySmoothCoef = (1.0 - exp(-1.0 / (0.35 * sampleRate))).toFloat()
        gainSmoothCoef = (1.0 - exp(-1.0 / (0.45 * sampleRate))).toFloat()

        reset()
    }

    /**
     * Processes a stereo frame (inL, inR) and returns spatialized (outL, outR) in outFrame.
     */
    fun processFrame(
        inL: Float,
        inR: Float,
        mode: SpatialMode,
        rotationSeconds: Float,
        intensity: Float,
        outFrame: FloatArray
    ) {
        if (mode == SpatialMode.OFF) {
            outFrame[0] = inL
            outFrame[1] = inR
            return
        }

        val isSixteenD = mode == SpatialMode.SIXTEEN_D
        val rotSecs = rotationSeconds.coerceIn(3f, 20f)
        val baseSpeed = (TWO_PI / (rotSecs * sampleRate))
        val maxDelay = MAX_ITD_SECONDS * sampleRate * (0.85f + 0.15f * intensity)
        val shadeAmount = 0.38f + 0.42f * intensity
        val roomMix = (if (isSixteenD) 0.12f else 0.08f) * (0.6f + 0.4f * intensity)
        val bassBoostMultiplier = 1.25f + 0.20f * intensity

        // 1. Linkwitz-Riley 2-Pole Crossover for Pristine Phase Alignment
        lpL1 += (inL - lpL1) * crossoverCoef
        lpL2 += (lpL1 - lpL2) * crossoverCoef
        lpR1 += (inR - lpR1) * crossoverCoef
        lpR2 += (lpR1 - lpR2) * crossoverCoef
        val lowL = lpL2
        val lowR = lpR2

        val highL = inL - lowL
        val highR = inR - lowR

        // 2. Centered Solid Bass + Dynamic Kick Punch & Sub-Harmonic Warmth
        val monoBass = (lowL + lowR) * 0.5f
        val bassPunch = detectKickAndEnrich(monoBass, rotSecs)
        val totalBass = monoBass * bassBoostMultiplier + bassPunch * 0.35f

        // 3. Update Continuous Angular Momentum on Beat Hits
        vocalMover.advance(baseSpeed)
        if (isSixteenD) {
            // In 16D, beat layer counter-rotates starting 180° opposite from vocals
            beatMover.advance(-baseSpeed * 1.1f)
            airMover.advance(baseSpeed * 1.5f)
        } else {
            // In 8D, beat orbits together with vocals
            beatMover.advance(baseSpeed)
        }

        // 4. Render Spatial Layers
        var movedL: Float
        var movedR: Float
        val centerBass: Float

        if (isSixteenD) {
            // 16D Duality: Vocals on one side, Beats on the opposite side
            airL1 += (highL - airL1) * airSplitCoef
            airR1 += (highR - airR1) * airSplitCoef
            val bodyL = airL1
            val bodyR = airR1
            val airL = highL - airL1
            val airR = highR - airR1

            // 35% sub-bass center anchor prevents ear fatigue; 65% travels with the beat mover
            centerBass = totalBass * 0.35f
            val movingBass = totalBass * 0.65f

            // A. Vocal/Melody orbit (with subtle elevation oscillation)
            val bodyElevation = sin(vocalMover.azimuth * 0.7f) * 0.35f * intensity
            vocalMover.render3D((bodyL + bodyR) * 0.5f, maxDelay, shadeAmount, bodyElevation)

            // B. Opposite Beat/Rhythm orbit (counter-traveling on the 180° opposite side)
            val beatElevation = cos(beatMover.azimuth * 0.9f) * -0.25f * intensity
            beatMover.render3D(movingBass, maxDelay, shadeAmount * 0.65f, beatElevation)

            // C. Air Percussion 3D Lissajous Spherical Orbit (> 3.6 kHz)
            val airElevation = cos(airMover.azimuth * 2.0f) * 0.55f * intensity
            airMover.render3D((airL + airR) * 0.5f, maxDelay * 0.9f, shadeAmount * 0.5f, airElevation)

            movedL = vocalMover.outL + beatMover.outL + airMover.outL
            movedR = vocalMover.outR + beatMover.outR + airMover.outR
        } else {
            // 8D Mode: Entire song (vocals + beat) orbits together around the head
            centerBass = totalBass * 0.30f
            val movingBass = totalBass * 0.70f
            val fullMix = (highL + highR) * 0.5f + movingBass
            vocalMover.render3D(fullMix, maxDelay, shadeAmount, elevation = 0f)
            movedL = vocalMover.outL
            movedR = vocalMover.outR
        }

        // 5. Early Reflection Acoustic Room (Provides natural 3D depth without vocal wash)
        val reflL = earlyReflLeft[reflWriteLeft]
        val reflR = earlyReflRight[reflWriteRight]
        earlyReflLeft[reflWriteLeft] = movedL * 0.8f + reflR * 0.22f
        earlyReflRight[reflWriteRight] = movedR * 0.8f + reflL * 0.22f
        reflWriteLeft = (reflWriteLeft + 1) % earlyReflLeft.size
        reflWriteRight = (reflWriteRight + 1) % earlyReflRight.size

        // 6. Recombine: Centered Anchor Bass + 3D Spatial Audio + Stereo Spread + Acoustic Depth
        val sideSpread = (highL - highR) * 0.35f
        var outL = centerBass + sideSpread + movedL * (1f - roomMix) + reflL * roomMix
        var outR = centerBass - sideSpread + movedR * (1f - roomMix) + reflR * roomMix

        // 7. Constant-Loudness Energy Compensation (Original volume never drops)
        val inPwr = (inL * inL + inR * inR) * 0.5f
        val outPwr = (outL * outL + outR * outR) * 0.5f
        inputEnergy += (inPwr - inputEnergy) * energySmoothCoef
        outputEnergy += (outPwr - outputEnergy) * energySmoothCoef

        val targetGain = (sqrt(inputEnergy / (outputEnergy + 1e-8f)) * 1.12f).coerceIn(0.95f, 2.2f)
        makeUpGain += (targetGain - makeUpGain) * gainSmoothCoef

        outL *= makeUpGain
        outR *= makeUpGain

        // 8. Output with transparent soft-knee saturation
        outFrame[0] = softLimit(outL)
        outFrame[1] = softLimit(outR)

        vocalMover.wrap()
        beatMover.wrap()
        airMover.wrap()
        liveVocalAngle = vocalMover.azimuth
        liveBeatAngle = beatMover.azimuth
        liveAirAngle = airMover.azimuth
    }

    fun getTelemetry(): SpatialTelemetry = SpatialTelemetry(
        vocalAngle = liveVocalAngle,
        beatAngle = liveBeatAngle,
        airAngle = liveAirAngle,
        lastBeatAtNanos = lastBeatAtNanos
    )

    private fun detectKickAndEnrich(bassSample: Float, rotSecs: Float): Float {
        samplesSinceBeat++
        beatBlockEnergy += (bassSample * bassSample).toDouble()
        beatBlockFill++

        if (beatBlockFill >= beatBlockSize) {
            val rms = sqrt(beatBlockEnergy / beatBlockFill)
            beatBlockEnergy = 0.0
            beatBlockFill = 0

            val minGap = (0.12 * sampleRate).toLong()
            val isKick = bassAverageRms > 0 &&
                    rms > bassAverageRms * 1.28 &&
                    rms > 0.015 &&
                    samplesSinceBeat > minGap

            bassAverageRms = if (bassAverageRms == 0.0) rms else bassAverageRms * 0.90 + rms * 0.10

            val fallbackGap = (rotSecs / 4f * sampleRate).toLong().coerceAtLeast((1.2 * sampleRate).toLong())
            val isFallback = samplesSinceBeat > fallbackGap

            if (isKick || isFallback) {
                samplesSinceBeat = 0
                lastBeatAtNanos = currentNanoTime()

                // Inject smooth angular acceleration on the beat
                val kickMomentum = (0.35f * (TWO_PI / sampleRate)).coerceAtLeast(0.0001f)
                vocalMover.injectMomentum(kickMomentum)
                beatMover.injectMomentum(-kickMomentum * 1.2f)
                airMover.injectMomentum(kickMomentum * 1.4f)

                // Trigger dynamic bass punch impulse
                dynamicBassPunch = 1.0f
            }
        }

        // Smoothly decay bass punch envelope (~80 ms punch tail)
        dynamicBassPunch *= 0.9996f
        val punchHarmonic = tanh(bassSample * 1.4f) * dynamicBassPunch
        return punchHarmonic
    }

    fun reset() {
        lpL1 = 0f; lpL2 = 0f; lpR1 = 0f; lpR2 = 0f
        airL1 = 0f; airR1 = 0f
        beatBlockFill = 0
        beatBlockEnergy = 0.0
        bassAverageRms = 0.0
        samplesSinceBeat = 0
        dynamicBassPunch = 0f
        inputEnergy = 1e-5f
        outputEnergy = 1e-5f
        makeUpGain = 1.15f
        reflWriteLeft = 0
        reflWriteRight = 0

        if (::earlyReflLeft.isInitialized) {
            earlyReflLeft.fill(0f)
            earlyReflRight.fill(0f)
        }
        if (::vocalMover.isInitialized) {
            vocalMover.reset(0f)
            beatMover.reset(PI.toFloat())
            airMover.reset(HALF_PI)
        }
    }

    private fun onePole(cutoffHz: Float): Float =
        (1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)).toFloat()

    private fun softLimit(x: Float): Float {
        val magnitude = abs(x)
        if (magnitude <= 0.88f) return x
        val over = (magnitude - 0.88f) / 0.12f
        val limited = 0.88f + 0.12f * tanh(over)
        return if (x < 0) -limited else limited
    }

    private class ContinuousSpatialMover(
        delaySize: Int,
        private val shadeCoef: Float
    ) {
        var azimuth = 0f
        private var angularVelocity = 0f
        private var momentumBoost = 0f

        var outL = 0f
        var outR = 0f

        private val delay = FloatArray(delaySize)
        private var writePos = 0
        private var shadeL = 0f
        private var shadeR = 0f

        fun injectMomentum(boost: Float) {
            momentumBoost = boost
        }

        fun advance(baseVelocity: Float) {
            momentumBoost *= 0.9997f
            angularVelocity = baseVelocity + momentumBoost
            azimuth += angularVelocity
        }

        fun render3D(
            x: Float,
            maxDelay: Float,
            shadeAmount: Float,
            elevation: Float
        ) {
            val pan = sin(azimuth)
            val depth = (1f - cos(azimuth)) * 0.5f

            delay[writePos] = x
            val nearSample = x
            val farDelaySamples = abs(pan) * maxDelay
            val farSample = readCubicHermiteDelay(farDelaySamples)
            writePos = (writePos + 1) % delay.size

            val leftRaw = if (pan > 0) farSample else nearSample
            val rightRaw = if (pan < 0) farSample else nearSample

            val panAngle = (pan + 1f) * (PI.toFloat() / 4f)
            val farFloor = 0.18f
            val gainL = farFloor + (1f - farFloor) * cos(panAngle)
            val gainR = farFloor + (1f - farFloor) * sin(panAngle)

            shadeL += (leftRaw - shadeL) * shadeCoef
            shadeR += (rightRaw - shadeR) * shadeCoef

            val shadeFactorL = (maxOf(pan, 0f) + depth * 0.35f - elevation * 0.2f).coerceIn(0f, 1f) * shadeAmount
            val shadeFactorR = (maxOf(-pan, 0f) + depth * 0.35f - elevation * 0.2f).coerceIn(0f, 1f) * shadeAmount

            val tonedL = leftRaw - (leftRaw - shadeL) * shadeFactorL
            val tonedR = rightRaw - (rightRaw - shadeR) * shadeFactorR

            val depthAttenuation = 1f - 0.06f * depth + (elevation * 0.05f)
            outL = tonedL * gainL * depthAttenuation
            outR = tonedR * gainR * depthAttenuation
        }

        private fun readCubicHermiteDelay(delaySamples: Float): Float {
            val size = delay.size
            val intDelay = delaySamples.toInt()
            val frac = delaySamples - intDelay

            val i1 = (writePos - intDelay + size) % size
            val i0 = (i1 - 1 + size) % size
            val i2 = (i1 + 1) % size
            val i3 = (i1 + 2) % size

            val y0 = delay[i0]
            val y1 = delay[i1]
            val y2 = delay[i2]
            val y3 = delay[i3]

            val c0 = y1
            val c1 = 0.5f * (y2 - y0)
            val c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3
            val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)

            return ((c3 * frac + c2) * frac + c1) * frac + c0
        }

        fun wrap() {
            while (azimuth > TWO_PI) azimuth -= TWO_PI
            while (azimuth < -TWO_PI) azimuth += TWO_PI
        }

        fun reset(initialAngle: Float) {
            azimuth = initialAngle
            angularVelocity = 0f
            momentumBoost = 0f
            delay.fill(0f)
            writePos = 0
            shadeL = 0f
            shadeR = 0f
        }
    }

    private companion object {
        const val TWO_PI = (2 * PI).toFloat()
        const val HALF_PI = (PI / 2).toFloat()
        const val CROSSOVER_HZ = 160f
        const val AIR_SPLIT_HZ = 3600f
        const val PINNA_SHADE_HZ = 3200f
        const val MAX_ITD_SECONDS = 0.00068f
        const val REFL_LEFT_SECONDS = 0.023f
        const val REFL_RIGHT_SECONDS = 0.029f
    }
}

/** Cross-platform monotonic time in nanoseconds */
expect fun currentNanoTime(): Long
