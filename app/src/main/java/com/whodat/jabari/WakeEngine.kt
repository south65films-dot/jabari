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
/**
 * Events: "WAKE" (open Claude; the mic has been released), "RECORDING" (audio started),
 * "SAVED:<file name>" (audio finished), "VIDEO" (start video), "STOP" (stop command), "PIN" (drop pin), "PANIC"/"SILENT" (safety alerts).
 *
 * Voice commands: "Jabari" alone opens Claude. "Jabari, record" starts recording,
 * "Jabari, stop" stops it. After "Jabari" the engine waits briefly for a command
 * before opening Claude. While recording, "Jabari" alone is ignored.
 */
class WakeEngine(private val ctx: Context, private val onEvent: (String) -> Unit) {

    private val sampleRate = 16000
    @Volatile private var running = false
    private var thread: Thread? = null
    private var spotter: KeywordSpotter? = null
    private val recorder = Recorder(ctx, sampleRate)
    private val commandWindowMs = 1500L
    val isRecording: Boolean get() = recorder.isRecording
    /** Set by the service while a video is recording. */
    @Volatile var videoActive = false
    @Volatile private var recordRequested = false
    /** Ask the loop to start an audio recording on its next cycle. */
    fun requestRecord() { recordRequested = true }

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
            var pendingSince = 0L // when "Jabari" was heard, 0 if not waiting
            try {
                record.startRecording()
                loop@ while (running) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    if (recordRequested && !recorder.isRecording) {
                        recordRequested = false; recorder.start(); onEvent("RECORDING")
                    }
                    if (recorder.isRecording) recorder.write(buffer, n)
                    val samples = FloatArray(n) { buffer[it] / 32768.0f }
                    stream.acceptWaveform(samples, sampleRate = sampleRate)
                    while (kws.isReady(stream)) {
                        kws.decode(stream)
                        val word = kws.getResult(stream).keyword
                        if (word.isBlank()) continue
                        kws.reset(stream)
                        Log.i(TAG, "Heard: $word")
                        val waiting = pendingSince > 0 &&
                            System.currentTimeMillis() - pendingSince < commandWindowMs
                        when (word) {
                            "JABARI" -> pendingSince = System.currentTimeMillis()
                            "RECORD" -> if (waiting) {
                                pendingSince = 0
                                if (!recorder.isRecording && !videoActive) { recorder.start(); onEvent("RECORDING") }
                            }
                            "VIDEO" -> if (waiting) {
                                pendingSince = 0
                                if (!videoActive) onEvent("VIDEO")
                            }
                            "PIN" -> if (waiting) {
                                pendingSince = 0
                                onEvent("PIN")
                            }
                            "PANIC" -> if (waiting) { pendingSince = 0; onEvent("PANIC") }
                            "SILENT" -> if (waiting) { pendingSince = 0; onEvent("SILENT") }
                            "STOP" -> if (waiting) {
                                pendingSince = 0
                                recorder.stop()?.let { onEvent("SAVED:$it") }
                                onEvent("STOP")
                            }
                        }
                    }
                    // "Jabari" with no command after it: open Claude (not while recording).
                    if (pendingSince > 0 && System.currentTimeMillis() - pendingSince >= commandWindowMs) {
                        pendingSince = 0
                        if (!recorder.isRecording && !videoActive) {
                            running = false
                            onEvent("WAKE")
                            break@loop
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listening failed", e)
            } finally {
                try { recorder.stop()?.let { onEvent("SAVED:$it") } } catch (_: Exception) { }
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
