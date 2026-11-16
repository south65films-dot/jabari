package com.whodat.jabari

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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

class ListenerService : Service() {

    private var engine: WakeEngine? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Jabari listener", NotificationManager.IMPORTANCE_LOW)
        )
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification("Starting…"), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            ev.startsWith("SAVED:") -> {
                beep(); beep()
                update("Saved ${ev.removePrefix("SAVED:")} to Music/Jabari Recordings")
            }
        }
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
