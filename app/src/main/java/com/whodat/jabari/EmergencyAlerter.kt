package com.whodat.jabari

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Sends a safety alert email with the phone's location over the internet (works on
 * Wi-Fi / hotspot; no cell service needed). Credentials are entered on the device,
 * never stored in the app's source.
 */
class EmergencyAlerter(private val ctx: Context) {

    @SuppressLint("MissingPermission")
    private fun lastFix(): Location? {
        return try {
            val lm = ctx.getSystemService(LocationManager::class.java)
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) {
            Log.w(TAG, "No location", e); null
        }
    }

    /** Builds and sends the email on a background thread. onResult reports success. */
    fun send(modeLabel: String, onResult: (Boolean, String) -> Unit) {
        val to = Config.emailTo(ctx)
        val user = Config.smtpUser(ctx)
        val pass = Config.smtpPass(ctx)
        if (to.isBlank() || user.isBlank() || pass.isBlank()) {
            onResult(false, "Alert not set up. Add email + password in Jabari Listener.")
            return
        }
        val loc = lastFix()
        Thread {
            val ok = try {
                val time = SimpleDateFormat("EEE MMM d, h:mm a z", Locale.US).format(Date())
                val where = if (loc != null)
                    "Location: %.6f, %.6f (accuracy ~%dm)\nMap: https://maps.google.com/?q=%f,%f"
                        .format(Locale.US, loc.latitude, loc.longitude, loc.accuracy.toInt(), loc.latitude, loc.longitude)
                else "Location: not available (GPS had no recent fix)."

                val props = Properties().apply {
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.connectiontimeout", "15000")
                    put("mail.smtp.timeout", "15000")
                    put("mail.smtp.writetimeout", "15000")
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
                })
                val msg = MimeMessage(session).apply {
                    setFrom(InternetAddress(user))
                    for (addr in to.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }) {
                        addRecipient(Message.RecipientType.TO, InternetAddress(addr))
                    }
                    subject = "JABARI ALERT — $modeLabel"
                    setText("This is an automatic safety alert from Demario's Jabari phone.\n\n" +
                            "$modeLabel triggered at $time.\n\n$where\n\n" +
                            "Recording was started on the device.")
                }
                Transport.send(msg)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Email failed", e); false
            }
            onResult(ok, if (ok) "Alert emailed." else "Alert could NOT send (check internet/setup).")
        }.apply { name = "jabari-alert"; start() }
    }

    companion object {
        private const val TAG = "JabariAlert"
        private const val SMTP_HOST = "smtp.ionos.com"
        private const val SMTP_PORT = "587"
    }
}
