package com.whodat.jabari

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.getStringExtra("email_to")?.let { Config.setEmailTo(this, it) }
        intent.getStringExtra("smtp_user")?.let { Config.setSmtpUser(this, it) }
        intent.getStringExtra("smtp_pass")?.let { Config.setSmtpPass(this, it) }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this)

        layout.addView(TextView(this).apply { text = "Jabari Listener"; textSize = 24f })
        layout.addView(TextView(this).apply { text = "Say \"Jabari\" to open Claude.\nSay \"Jabari, record\" to start recording and \"Jabari, stop\" to save it (Music/Jabari Recordings).\nSay \"Jabari, video\" to record video and \"Jabari, stop\" to save it (Movies/Jabari Videos).\nSay \"Jabari, drop a pin\" to save your location (Documents/Jabari/Jabari Pins.txt)." })
        layout.addView(status)
        layout.addView(button("Turn on tap helper (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("Start listening") { ListenerService.start(this); refresh() })
        layout.addView(button("Stop listening") { ListenerService.stop(this); refresh() })

        setContentView(layout)
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS), 1
        )
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        status.text = "Listening: " + (if (Config.enabled(this)) "on" else "off") +
            "\nSafety alert: " + (if (Config.alertConfigured(this)) "set up" else "NOT set up")
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }
}
