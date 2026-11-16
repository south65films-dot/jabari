package com.whodat.jabari

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Jabari, drop a pin": saves the current location (with a Google Maps link and,
 * when online, a street address) to Documents/Jabari/Jabari Pins.txt.
 */
class PinDropper(private val ctx: Context) {

    @SuppressLint("MissingPermission")
    fun drop(onDone: (String) -> Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) { onDone("Location is off. Turn on Location in Settings."); return }
        val fallback = lm.getLastKnownLocation(provider)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= 30) {
            lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                save(loc ?: fallback, onDone)
            }
        } else {
            save(fallback, onDone)
        }
    }

    private fun save(loc: Location?, onDone: (String) -> Unit) {
        if (loc == null) { onDone("Couldn't get a location fix. Try again near a window."); return }
        Thread {
            val time = SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.US).format(Date())
            val coords = "%.6f, %.6f".format(Locale.US, loc.latitude, loc.longitude)
            val address = try {
                @Suppress("DEPRECATION")
                Geocoder(ctx, Locale.US).getFromLocation(loc.latitude, loc.longitude, 1)
                    ?.firstOrNull()?.getAddressLine(0)
            } catch (_: Exception) { null }
            val entry = buildString {
                append("$time\n")
                if (address != null) append("$address\n")
                append("$coords (accuracy about ${loc.accuracy.toInt()} m)\n")
                append("https://maps.google.com/?q=${loc.latitude},${loc.longitude}\n\n")
            }
            val log = File(ctx.filesDir, "pins.txt")
            log.appendText(entry)
            publish(log.readText())
            onDone("Pin saved: " + (address ?: coords))
        }.start()
    }

    /** Keeps a readable copy in Documents/Jabari so it shows in the Files app. */
    private fun publish(text: String) {
        try {
            val resolver = ctx.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val rel = Environment.DIRECTORY_DOCUMENTS + "/Jabari/"
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(rel, FILE_NAME), null
            )?.use { c ->
                while (c.moveToNext()) {
                    resolver.delete(android.content.ContentUris.withAppendedId(collection, c.getLong(0)), null, null)
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }
            resolver.insert(collection, values)?.let { uri ->
                resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        } catch (_: Exception) { }
    }

    companion object { private const val FILE_NAME = "Jabari Pins.txt" }
}
