package com.whodat.jabari

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/** Takes a single still photo with the rear camera, no preview screen. */
class PhotoCapture(private val ctx: Context, private val owner: LifecycleOwner) {

    fun capture(onSaved: (File) -> Unit, onError: (String) -> Unit) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, capture)

                val file = File(ctx.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    options, ContextCompat.getMainExecutor(ctx),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                            provider.unbindAll()
                            onSaved(file)
                        }
                        override fun onError(e: ImageCaptureException) {
                            provider.unbindAll()
                            Log.e(TAG, "Capture error", e)
                            onError(e.message ?: "capture failed")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera error", e)
                onError(e.message ?: "camera unavailable")
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    companion object { private const val TAG = "JabariPhoto" }
}
