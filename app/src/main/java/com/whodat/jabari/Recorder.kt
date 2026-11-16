package com.whodat.jabari

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the same mic audio the wake engine hears to a WAV file (16 kHz mono),
 * then copies it to Music/Jabari Recordings so it shows up in the Files app.
 */
class Recorder(private val ctx: Context, private val sampleRate: Int) {

    private var file: RandomAccessFile? = null
    private var tmp: File? = null
    private var dataBytes = 0L
    private var name = ""

    val isRecording: Boolean get() = file != null

    fun start() {
        if (isRecording) return
        name = "Jabari_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".wav"
        val f = File(ctx.cacheDir, name)
        tmp = f
        file = RandomAccessFile(f, "rw").apply {
            setLength(0)
            write(ByteArray(44)) // header filled in on stop
        }
        dataBytes = 0
    }

    fun write(samples: ShortArray, count: Int) {
        val out = file ?: return
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[2 * i] = (samples[i].toInt() and 0xff).toByte()
            bytes[2 * i + 1] = (samples[i].toInt() shr 8 and 0xff).toByte()
        }
        out.write(bytes)
        dataBytes += bytes.size
    }

    /** Finishes the file and returns its name, or null if nothing was recording. */
    fun stop(): String? {
        val out = file ?: return null
        out.seek(0)
        out.write(header(dataBytes))
        out.close()
        file = null
        val src = tmp ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Jabari Recordings")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
            src.delete()
        }
        return name
    }

    private fun header(dataLen: Long): ByteArray {
        val byteRate = sampleRate * 2
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + dataLen).toInt())
        b.put("WAVE".toByteArray()); b.put("fmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(sampleRate); b.putInt(byteRate); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataLen.toInt())
        return b.array()
    }
}
