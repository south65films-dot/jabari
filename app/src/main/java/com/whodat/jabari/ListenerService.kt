package com.whodat.jabari

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
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

    private var porcupine: PorcupineManager? = null
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
        stopPorcupine()
        val key = Config.accessKey(this)
        if (key.isBlank()) {
            update("No Picovoice key yet. Open Jabari Listener to add it.")
            return
        }
        try {
            val builder = PorcupineManager.Builder().setAccessKey(key).setSensitivity(0.6f)
            val custom = Config.keywordFile(this)
            val label: String
            if (custom.exists()) {
                builder.setKeywordPath(custom.absolutePath)
                label = "Jabari"
            } else {
                // Test word until the custom "Jabari" file is loaded.
                builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                label = "Jarvis (test word)"
            }
            porcupine = builder.build(applicationContext) { onWake() }
            porcupine?.start()
            update("Listening for \"$label\"")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine failed", e)
            update("Wake word error: ${e.message}")
        }
    }

    private fun onWake() {
        Log.i(TAG, "Wake word heard")
        stopPorcupine() // free the mic for Claude
        Wake.trigger()
        openClaude()
        update("Heard you. Back to listening shortly.")
        handler.postDelayed({ startListening() }, Config.COOLDOWN_MS)
    }

    private fun openClaude() {
        val assist = Intent(Intent.ACTION_VOICE_ASSIST)
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

    private fun stopPorcupine() {
        try {
            porcupine?.stop()
            porcupine?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Stop failed", e)
        }
        porcupine = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopPorcupine()
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
