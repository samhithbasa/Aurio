package com.samhith.aurio.data.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * openWakeWord inference pipeline: three small TFLite models chained together.
 *
 *   audio (16 kHz int16) -> melspectrogram -> speech embeddings -> wake-word score
 *
 * Each 80 ms of audio produces 8 mel frames; the last 76 mel frames are turned into one 96-value
 * speech embedding, and the last N embeddings (whatever the trained model expects, ~1.3 s of audio)
 * give a score between 0 and 1 for "was the wake phrase just spoken".
 *
 * This replaces the earlier Vosk approach, which was a speech-to-text engine bent into a wake-word
 * detector: it had to force every sound into the nearest allowed phrase, so it both missed the wake
 * word and fired on music.
 */
class OpenWakeWordModel private constructor(
    private val melModel: Interpreter,
    private val embeddingModel: Interpreter,
    private val wakeWordModel: Interpreter,
    private val melBands: Int,
    private val embeddingWindow: Int,
    private val embeddingSize: Int,
    private val wakeWordFrames: Int,
    /**
     * True when the trained model wants [1, values, frames] instead of [1, frames, values].
     * Which one you get depends on the notebook that exported it, so it is detected, not assumed.
     */
    private val wakeWordTransposed: Boolean
) {

    private val TAG = "OpenWakeWord"

    /**
     * The audio handed to the mel model: the newest 80 ms plus [CONTEXT_SAMPLES] of the audio just
     * before it. The model consumes that lead-in to produce its first frame, so without the overlap
     * each chunk would yield only ~5 frames instead of 8 and speech would reach the wake-word model
     * stretched to the wrong time scale - it would never match.
     */
    private val audioWindow = FloatArray(CHUNK_SAMPLES + CONTEXT_SAMPLES)
    private var audioFilled = 0

    /** Most recent mel frames, newest last; only [embeddingWindow] are kept. */
    private val melFrames = ArrayDeque<FloatArray>()

    /** Most recent speech embeddings, newest last; only [wakeWordFrames] are kept. */
    private val embeddings = ArrayDeque<FloatArray>()

    /**
     * Feeds one chunk of microphone audio (16 kHz mono int16).
     *
     * @return the wake-word score for this moment, or null while the pipeline is still filling up
     * (it needs roughly the first second of audio before it can score anything).
     */
    fun accept(samples: ShortArray, count: Int): Float? {
        if (!slideAudioWindow(samples, count)) return null // still priming the first 110 ms
        appendMelFrames()
        if (melFrames.size < embeddingWindow) return null

        embeddings.addLast(computeEmbedding())
        while (embeddings.size > wakeWordFrames) embeddings.removeFirst()
        if (embeddings.size < wakeWordFrames) return null

        return score()
    }

    /** Clears the audio history, e.g. after a wake or after the mic was taken by another app. */
    fun reset() {
        audioFilled = 0
        java.util.Arrays.fill(audioWindow, 0f)
        melFrames.clear()
        embeddings.clear()
    }

    fun close() {
        melModel.close()
        embeddingModel.close()
        wakeWordModel.close()
    }

    /**
     * Shifts the newest audio into [audioWindow], keeping the previous [CONTEXT_SAMPLES] in front
     * of it. Returns false until enough audio has arrived to fill the window once.
     */
    private fun slideAudioWindow(samples: ShortArray, count: Int): Boolean {
        val take = minOf(count, audioWindow.size)
        val keep = audioWindow.size - take
        System.arraycopy(audioWindow, take, audioWindow, 0, keep)
        // The models were trained on raw int16 values kept as floats, not on normalized audio
        for (i in 0 until take) {
            audioWindow[keep + i] = samples[count - take + i].toFloat()
        }
        audioFilled = minOf(audioFilled + count, audioWindow.size)
        return audioFilled >= audioWindow.size
    }

    private fun appendMelFrames() {
        val input = Array(1) { audioWindow.copyOf() }

        val shape = melModel.getOutputTensor(0).shape() // [1, 1, frames, bands]
        val frames = shape[2]
        val output = Array(1) { Array(1) { Array(frames) { FloatArray(shape[3]) } } }
        melModel.run(input, output)

        for (frame in output[0][0]) {
            // openWakeWord's scaling, applied before the embedding model sees the frame
            for (i in frame.indices) frame[i] = frame[i] / 10f + 2f
            melFrames.addLast(frame)
        }
        while (melFrames.size > embeddingWindow) melFrames.removeFirst()
    }

    private fun computeEmbedding(): FloatArray {
        val window = Array(1) { Array(embeddingWindow) { Array(melBands) { FloatArray(1) } } }
        melFrames.forEachIndexed { frameIndex, frame ->
            for (band in 0 until melBands) {
                window[0][frameIndex][band][0] = frame[band]
            }
        }
        val output = Array(1) { Array(1) { Array(1) { FloatArray(embeddingSize) } } }
        embeddingModel.run(window, output)
        return output[0][0][0]
    }

    private fun score(): Float {
        val input = if (wakeWordTransposed) {
            Array(1) { Array(embeddingSize) { FloatArray(wakeWordFrames) } }
        } else {
            Array(1) { Array(wakeWordFrames) { FloatArray(embeddingSize) } }
        }
        embeddings.forEachIndexed { frame, embedding ->
            if (wakeWordTransposed) {
                for (value in 0 until embeddingSize) input[0][value][frame] = embedding[value]
            } else {
                System.arraycopy(embedding, 0, input[0][frame], 0, embeddingSize)
            }
        }

        // The exported model's output shape depends on how it was converted, so it is read from the
        // model rather than assumed: it may be a single score or a small column of them.
        val shape = wakeWordModel.getOutputTensor(0).shape()
        val rows = shape.getOrElse(0) { 1 }
        val columns = shape.getOrElse(1) { 1 }
        val output = Array(rows) { FloatArray(columns) }
        wakeWordModel.run(input, output)

        var best = 0f
        for (row in output) {
            for (value in row) {
                if (value > best) best = value
            }
        }
        return best
    }

    companion object {
        /** 80 ms at 16 kHz: one pipeline step. */
        const val CHUNK_SAMPLES = 1280

        /** Lead-in the mel model consumes before its first frame: 3 hops of 160 samples (30 ms). */
        const val CONTEXT_SAMPLES = 480

        private const val MEL_MODEL = "melspectrogram.tflite"
        private const val EMBEDDING_MODEL = "embedding_model.tflite"

        /**
         * Loads the pipeline, or returns null if a model is missing or unreadable, so the assistant
         * can fall back to tap-to-talk instead of crashing.
         */
        fun load(context: Context, wakeWordAsset: String): OpenWakeWordModel? {
            return try {
                val options = Interpreter.Options().apply { numThreads = 1 }
                val mel = Interpreter(readAsset(context, MEL_MODEL), options)
                // Fixed input: one 80 ms chunk plus its lead-in. Sized once, not per chunk.
                mel.resizeInput(0, intArrayOf(1, CHUNK_SAMPLES + CONTEXT_SAMPLES))
                mel.allocateTensors()
                val embedding = Interpreter(readAsset(context, EMBEDDING_MODEL), options)
                val wakeWord = Interpreter(readAsset(context, wakeWordAsset), options)

                // Read the shapes from the models themselves, so a differently trained wake-word
                // model (longer phrase, different context length) keeps working unchanged.
                val embeddingInput = embedding.getInputTensor(0).shape()   // [1, frames, bands, 1]
                val wakeWordInput = wakeWord.getInputTensor(0).shape()     // [1, frames, size]

                // Pin one input at a time and let TFLite recompute the graph, so the output shapes
                // reported below are the real ones rather than whatever the file was exported with.
                embedding.resizeInput(0, intArrayOf(1, embeddingInput[1], embeddingInput[2], 1))
                embedding.allocateTensors()
                wakeWord.resizeInput(0, intArrayOf(1, wakeWordInput[1], wakeWordInput[2]))
                wakeWord.allocateTensors()

                val embeddingOutput = embedding.getOutputTensor(0).shape() // [1, 1, 1, size]
                val embeddingSize = embeddingOutput[3]

                // [1, frames, 96] or [1, 96, frames]: whichever axis matches the embedding size
                // is the values axis, and the other one is time.
                val transposed = wakeWordInput[1] == embeddingSize && wakeWordInput[2] != embeddingSize
                val frames = if (transposed) wakeWordInput[2] else wakeWordInput[1]

                OpenWakeWordModel(
                    melModel = mel,
                    embeddingModel = embedding,
                    wakeWordModel = wakeWord,
                    melBands = embeddingInput[2],
                    embeddingWindow = embeddingInput[1],
                    embeddingSize = embeddingSize,
                    wakeWordFrames = frames,
                    wakeWordTransposed = transposed
                ).also {
                    Log.d(
                        "OpenWakeWord",
                        "Loaded $wakeWordAsset | mel out ${mel.getOutputTensor(0).shape().toList()} " +
                                "(frames per 80 ms should be 8) | embedding in ${embeddingInput.toList()} " +
                                "out ${embeddingOutput.toList()} | wake in ${wakeWordInput.toList()} " +
                                "out ${wakeWord.getOutputTensor(0).shape().toList()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("OpenWakeWord", "Could not load wake-word models: ${e.message}")
                null
            }
        }

        /**
         * Reads a model into a direct buffer. Assets may be stored compressed, which rules out
         * memory-mapping them, and these models are only a few MB in total.
         */
        private fun readAsset(context: Context, name: String): ByteBuffer {
            val bytes = context.assets.open(name).use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .put(bytes)
                .apply { rewind() }
        }
    }
}
