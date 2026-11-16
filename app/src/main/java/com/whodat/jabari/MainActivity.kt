package com.whodat.jabari

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allows setup from the PC: adb shell am start -n com.whodat.jabari/.MainActivity --es access_key KEY
        intent.getStringExtra("access_key")?.let { if (it.isNotBlank()) Config.setAccessKey(this, it) }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this)
        val keyField = EditText(this).apply {
            hint = "Paste Picovoice AccessKey"
            setText(Config.accessKey(this@MainActivity))
        }

        layout.addView(TextView(this).apply { text = "Jabari Listener"; textSize = 24f })
        layout.addView(status)
        layout.addView(keyField)
        layout.addView(button("Save key") {
            Config.setAccessKey(this, keyField.text.toString()); refresh()
        })
        layout.addView(button("Load \"Jabari\" wake word file (.ppn)") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                PICK_PPN
            )
        })
        layout.addView(button("Turn on tap helper (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("Start listening") { ListenerService.start(this); refresh() })
        layout.addView(button("Stop listening") { ListenerService.stop(this); refresh() })

        setContentView(layout)
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1
        )
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    @Deprecated("Simple single-screen app")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PPN && resultCode == RESULT_OK) data?.data?.let { copyPpn(it) }
    }

    private fun copyPpn(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                Config.keywordFile(this).outputStream().use { input.copyTo(it) }
            }
            Toast.makeText(this, "Wake word loaded. Restart listening.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't load file: ${e.message}", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun refresh() {
        val key = if (Config.accessKey(this).isBlank()) "missing" else "saved"
        val word = if (Config.keywordFile(this).exists()) "Jabari (custom)" else "Jarvis (test word until you load the Jabari file)"
        val on = if (Config.enabled(this)) "on" else "off"
        status.text = "Key: $key\nWake word: $word\nListening: $on"
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }

    companion object { private const val PICK_PPN = 7 }
}
