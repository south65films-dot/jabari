package com.whodat.jabari

import android.content.Context

/** Settings stored on the phone. */
object Config {
    private const val PREFS = "jabari"
    private const val KEY_ENABLED = "enabled"

    const val CLAUDE_PACKAGE = "com.anthropic.claude"

    /** How long to stay quiet after a wake, so Claude can use the mic. */
    const val COOLDOWN_MS = 45_000L

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

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
