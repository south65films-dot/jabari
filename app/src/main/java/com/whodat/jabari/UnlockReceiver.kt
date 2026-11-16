package com.whodat.jabari

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Plays the intro when the phone is unlocked, if the intro is enabled. */
class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT && Config.introEnabled(context)) {
            SplashActivity.play(context)
        }
    }
}
