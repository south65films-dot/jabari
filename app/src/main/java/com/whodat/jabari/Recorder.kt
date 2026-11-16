package com.whodat.jabari

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the same mic audio the wake engine hears as AAC in an .m4a file
 * (48 kbps mono, about 22 MB per hour vs about 115 MB for WAV), then copies it
 * to Music/Jabari Recordings so it shows up in the Files app.
 */
class Recorder(private val ctx: Context, private val sampleRate: Int) {

    private val bitRate = 48_000
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var muxerStarted = false
    private var samplesIn = 0L
    private var tmp: File? = null
    private var name = ""
    private val info = MediaCodec.BufferInfo()

    val isRecording: Boolean get() = codec != null

    fun start() {
        if (isRecording) return
        name = "Jabari_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".m4a"
        val f = File(ctx.cacheDir, name).also { tmp = it }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(f.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        track = -1
        muxerStarted = false
        samplesIn = 0
    }

    fun write(samples: ShortArray, count: Int) {
        val c = codec ?: return
        var offset = 0
        while (offset < count) {
            val index = c.dequeueInputBuffer(10_000)
            if (index < 0) { drain(false); continue }
            val buf = c.getInputBuffer(index)!!
            buf.clear()
            val n = minOf(count - offset, buf.capacity() / 2)
            for (i in 0 until n) buf.putShort(samples[offset + i])
            val ptsUs = samplesIn * 1_000_000L / sampleRate
            c.queueInputBuffer(index, 0, n * 2, ptsUs, 0)
            samplesIn += n
            offset += n
        }
        drain(false)
    }

    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val m = muxer ?: return
        while (true) {
            val out = c.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                out == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = m.addTrack(c.outputFormat)
                    m.start()
                    muxerStarted = true
                }
                out >= 0 -> {
                    val buf = c.getOutputBuffer(out)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        m.writeSampleData(track, buf, info)
                    }
                    c.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Finishes the file and returns its name, or null if nothing was recording. */
    fun stop(): String? {
        val c = codec ?: return null
        val index = c.dequeueInputBuffer(10_000)
        if (index >= 0) {
            c.queueInputBuffer(index, 0, 0, samplesIn * 1_000_000L / sampleRate,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drain(true)
        c.stop(); c.release()
        codec = null
        try { if (muxerStarted) muxer?.stop() } catch (_: Exception) { }
        muxer?.release()
        muxer = null

        val src = tmp ?: return null
        if (!muxerStarted) { src.delete(); return null }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Jabari Recordings")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
            src.delete()
        }
        return name
    }
}
