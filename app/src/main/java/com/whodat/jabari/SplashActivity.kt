package com.whodat.jabari

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.widget.VideoView

/** Fullscreen intro: plays the Jabari video with sound, then closes itself. Tap to skip. */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        showing = true

        val video = VideoView(this)
        setContentView(video)
        video.setVideoURI(Uri.parse("android.resource://$packageName/raw/intro"))
        video.setOnPreparedListener { it.start() }
        video.setOnCompletionListener { finish() }
        video.setOnErrorListener { _, _, _ -> finish(); true }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) finish()
        return true
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }

    companion object {
        @Volatile var showing = false
        fun play(ctx: android.content.Context) {
            if (showing) return
            try {
                ctx.startActivity(
                    android.content.Intent(ctx, SplashActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }
}
