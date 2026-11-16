package com.whodat.jabari

import android.content.Context
import java.io.File

/** Settings stored on the phone. The Picovoice key is never hardcoded in the app. */
object Config {
    private const val PREFS = "jabari"
    private const val KEY_ACCESS = "access_key"
    private const val KEY_ENABLED = "enabled"

    const val CLAUDE_PACKAGE = "com.anthropic.claude"

    /** How long to stay quiet after a wake, so Claude can use the mic. */
    const val COOLDOWN_MS = 45_000L

    fun accessKey(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCESS, "") ?: ""

    fun setAccessKey(ctx: Context, key: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCESS, key.trim()).apply()

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

    /** Custom "Jabari" wake word file trained in the Picovoice console. */
    fun keywordFile(ctx: Context): File = File(ctx.filesDir, "jabari.ppn")
}

/** Shared state between the listener and the tap helper (same process). */
object Wake {
    @Volatile var lastTrigger: Long = 0L
    @Volatile var tapped: Boolean = true

    fun trigger() {
        lastTrigger = System.currentTimeMillis()
        tapped = false
    }

    fun isFresh(): Boolean = !tapped && System.currentTimeMillis() - lastTrigger < 10_000L
}
