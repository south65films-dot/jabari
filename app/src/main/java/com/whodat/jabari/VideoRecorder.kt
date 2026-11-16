package com.whodat.jabari

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records video (rear camera, up to 720p, with sound) without a preview screen,
 * saved to Movies/Jabari Videos.
 */
class VideoRecorder(private val ctx: Context, private val owner: LifecycleOwner) {

    private var recording: Recording? = null
    private var provider: ProcessCameraProvider? = null

    val isRecording: Boolean get() = recording != null

    @SuppressLint("MissingPermission")
    fun start(onSaved: (String) -> Unit, onError: (String) -> Unit) {
        if (isRecording) return
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val p = future.get().also { provider = it }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    )
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, capture)

                val name = "Jabari_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Jabari Videos")
                }
                val output = MediaStoreOutputOptions.Builder(
                    ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(values).build()

                recording = recorder.prepareRecording(ctx, output)
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(ctx)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            recording = null
                            p.unbindAll()
                            if (event.hasError()) onError("Video error ${event.error}")
                            else onSaved("$name.mp4")
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Video start failed", e)
                recording = null
                onError(e.message ?: "camera unavailable")
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun stop() {
        recording?.stop()
    }

    companion object { private const val TAG = "JabariVideo" }
}
