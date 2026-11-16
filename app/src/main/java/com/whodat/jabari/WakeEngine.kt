package com.whodat.jabari

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig

/**
 * Free, offline wake word using sherpa-onnx keyword spotting.
 * The model and keywords.txt ship inside the app (assets/kws), added by the GitHub build.
 */
class WakeEngine(private val ctx: Context, private val onWake: (String) -> Unit) {

    private val sampleRate = 16000
    @Volatile private var running = false
    private var thread: Thread? = null
    private var spotter: KeywordSpotter? = null

    private fun spotter(): KeywordSpotter {
        spotter?.let { return it }
        val dir = "kws"
        val config = KeywordSpotterConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$dir/encoder.onnx",
                    decoder = "$dir/decoder.onnx",
                    joiner = "$dir/joiner.onnx",
                ),
                tokens = "$dir/tokens.txt",
                modelType = "zipformer2",
                numThreads = 1,
            ),
            keywordsFile = "$dir/keywords.txt",
        )
        return KeywordSpotter(assetManager = ctx.assets, config = config).also { spotter = it }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val kws = spotter()
        running = true
        thread = Thread {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
            val stream = kws.createStream()
            val buffer = ShortArray(sampleRate / 10) // 100 ms
            try {
                record.startRecording()
                while (running) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    stream.acceptWaveform(samples, sampleRate = sampleRate)
                    while (kws.isReady(stream)) {
                        kws.decode(stream)
                        val word = kws.getResult(stream).keyword
                        if (word.isNotBlank()) {
                            kws.reset(stream)
                            Log.i(TAG, "Heard: $word")
                            running = false
                            onWake(word)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listening failed", e)
            } finally {
                try { record.stop() } catch (_: Exception) { }
                record.release() // free the mic for Claude
                stream.release()
            }
        }.apply { name = "jabari-wake"; start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    companion object { private const val TAG = "JabariWake" }
}
