package com.whodat.jabari

import android.content.Context

/** Settings stored on the phone. */
object Config {
    private const val PREFS = "jabari"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_EMAIL_TO = "email_to"
    private const val KEY_SMTP_USER = "smtp_user"
    private const val KEY_SMTP_PASS = "smtp_pass"

    const val CLAUDE_PACKAGE = "com.anthropic.claude"
    const val TRANSLATE_PACKAGE = "com.google.android.apps.translate"

    /** How long to stay quiet after a wake, so Claude can use the mic. */
    const val COOLDOWN_MS = 45_000L

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun emailTo(ctx: Context) = prefs(ctx).getString(KEY_EMAIL_TO, "") ?: ""
    fun setEmailTo(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_EMAIL_TO, v.trim()).apply()
    fun smtpUser(ctx: Context) = prefs(ctx).getString(KEY_SMTP_USER, "") ?: ""
    fun setSmtpUser(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_SMTP_USER, v.trim()).apply()
    fun smtpPass(ctx: Context) = prefs(ctx).getString(KEY_SMTP_PASS, "") ?: ""
    fun setSmtpPass(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_SMTP_PASS, v).apply()
    fun alertConfigured(ctx: Context) = emailTo(ctx).isNotBlank() && smtpUser(ctx).isNotBlank() && smtpPass(ctx).isNotBlank()

    private const val KEY_INTRO = "intro_enabled"
    fun introEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_INTRO, true)
    fun setIntroEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_INTRO, on).apply()

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

    // Translate (Transcribe) auto-tap state
    @Volatile var translateTrigger: Long = 0L
    @Volatile var translateTapped: Boolean = true
    @Volatile var inTranslate: Boolean = false

    fun triggerTranslate() {
        translateTrigger = System.currentTimeMillis()
        translateTapped = false
        inTranslate = true
    }

    fun isTranslateFresh(): Boolean =
        !translateTapped && System.currentTimeMillis() - translateTrigger < 12_000L
}
