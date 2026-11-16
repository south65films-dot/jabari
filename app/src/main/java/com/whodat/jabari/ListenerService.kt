package com.whodat.jabari

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService

class ListenerService : LifecycleService() {

    private var engine: WakeEngine? = null
    private val video by lazy { VideoRecorder(this, this) }
    private val pins by lazy { PinDropper(this) }
    private val alerter by lazy { EmergencyAlerter(this) }
    private val photo by lazy { PhotoCapture(this, this) }
    private var ringtone: android.media.Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder? { super.onBind(intent); return null }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jabari listener", NotificationManager.IMPORTANCE_LOW)
        )
        val type = if (Build.VERSION.SDK_INT >= 30)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification("Starting…"), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            Config.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        try {
            val e = engine ?: WakeEngine(this) { ev -> handler.post { onEngineEvent(ev) } }.also { engine = it }
            e.start()
            update("Listening for \"Jabari\"")
        } catch (e: Exception) {
            Log.e(TAG, "Wake engine failed", e)
            update("Wake word error: ${e.message}")
        }
    }

    private fun onEngineEvent(ev: String) {
        when {
            ev == "WAKE" -> onWake()
            ev == "RECORDING" -> { beep(); update("Recording… say \"Jabari, stop\"") }
            ev == "VIDEO" -> startVideo()
            ev == "PIN" -> { beep(); pins.drop { msg -> handler.post { beep(); update(msg) } } }
            ev == "GTRANSLATE" -> openTranslate()
            ev == "IDENTIFY" -> snapToClaude("What is in this picture? Identify it as specifically as you can.")
            ev == "TRANSLATE" -> snapToClaude("Translate any text in this image and tell me what it says. Note the source language.")
            ev == "PANIC" -> emergency("Panic Mode", loud = true)
            ev == "SILENT" -> emergency("Silent Alarm", loud = false)
            ev == "STOP" -> { stopAlarm(); if (video.isRecording) video.stop() }
            ev.startsWith("SAVED:") -> {
                beep(); beep()
                update("Saved ${ev.removePrefix("SAVED:")} to Music/Jabari Recordings")
            }
        }
    }

    private fun emergency(label: String, loud: Boolean) {
        if (loud) startAlarm()
        if (engine?.isRecording != true) {
            engine?.let { /* audio capture is inside the engine loop; recorder started via its command path */ }
        }
        // Start an audio recording for the record.
        try { startAudioFromService() } catch (_: Exception) { }
        pins.drop { }
        update(if (loud) "PANIC: alarm on, emailing $label…" else "SILENT: emailing $label…")
        alerter.send(label) { ok, msg -> handler.post { update(msg) } }
    }

    private fun startAudioFromService() {
        // Ask the engine to begin recording the mic it is already reading.
        engine?.requestRecord()
    }

    private fun startAlarm() {
        try {
            val am = getSystemService(android.media.AudioManager::class.java)
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM), 0)
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ringtone = android.media.RingtoneManager.getRingtone(this, uri).apply {
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        } catch (e: Exception) { Log.w(TAG, "Alarm failed", e) }
    }

    private fun stopAlarm() {
        try { ringtone?.stop() } catch (_: Exception) { }
        ringtone = null
    }

    private fun openTranslate() {
        val pkg = "com.google.android.apps.translate"
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            update("Translate open (use Conversation). Tap Start listening when done.")
        } else {
            update("Install Google Translate first.")
        }
    }

    private fun snapToClaude(prompt: String) {
        if (video.isRecording) { update("Can't snap while recording video"); return }
        beep()
        update("Taking photo…")
        photo.capture(
            onSaved = { file -> handler.post { saveImageToGallery(file); shareToClaude(file, prompt) } },
            onError = { msg -> handler.post { update("Photo failed: $msg") } }
        )
    }

    private fun saveImageToGallery(file: java.io.File) {
        Thread {
            try {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/Jabari")
                }
                val uri = contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )
                if (uri != null) contentResolver.openOutputStream(uri)?.use { o ->
                    file.inputStream().use { it.copyTo(o) }
                }
            } catch (e: Exception) { Log.w(TAG, "Gallery save failed", e) }
        }.start()
    }

    private fun shareToClaude(file: java.io.File, prompt: String) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                setPackage(Config.CLAUDE_PACKAGE)
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, prompt)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(send)
            update("Sent to Claude — tap send.")
        } catch (e: Exception) {
            Log.w(TAG, "Share failed", e)
            update("Couldn't open Claude with the photo.")
        }
    }

    private fun startVideo() {
        engine?.videoActive = true
        beep()
        update("Recording video… say \"Jabari, stop\"")
        video.start(
            onSaved = { name ->
                engine?.videoActive = false
                beep(); beep()
                update("Saved $name to Movies/Jabari Videos")
            },
            onError = { msg ->
                engine?.videoActive = false
                update("Video failed: $msg")
            }
        )
    }

    private fun beep() {
        try {
            android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) { }
    }

    private fun onWake() {
        Log.i(TAG, "Wake word heard")
        // The engine has already released the mic.
        Wake.trigger()
        openClaude()
        update("Heard you. Back to listening shortly.")
        handler.postDelayed({ startListening() }, Config.COOLDOWN_MS)
    }

    private fun openClaude() {
        val assist = Intent("android.intent.action.VOICE_ASSIST")
            .setPackage(Config.CLAUDE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(assist)
        } catch (e: Exception) {
            Log.w(TAG, "Assist launch failed, opening app instead", e)
            packageManager.getLaunchIntentForPackage(Config.CLAUDE_PACKAGE)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        }
    }

    private fun stopListening() {
        try { engine?.stop() } catch (e: Exception) { Log.w(TAG, "Stop failed", e) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (video.isRecording) video.stop()
        stopAlarm()
        stopListening()
        super.onDestroy()
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jabari")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "JabariListener"
        private const val CHANNEL = "jabari"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.whodat.jabari.STOP"

        fun start(ctx: Context) {
            Config.setEnabled(ctx, true)
            ctx.startForegroundService(Intent(ctx, ListenerService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, ListenerService::class.java).setAction(ACTION_STOP))
        }
    }
}
