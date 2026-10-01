package com.thomaspeoples.wraithbus.ntfy

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * The ntfy topic is a credential (anyone who knows it can read the alerts),
 * so it is typed in on the device and never baked into the APK -- a build
 * that carried it would hand it to everyone who downloads the file.
 */
object TopicStore {
    private const val PREFS = "ntfy"
    private const val KEY_TOPIC = "topic"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOPIC, "").orEmpty()

    /** Saves the topic and restarts the listener on it; blank turns alerts off. */
    fun set(context: Context, topic: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_TOPIC, topic.trim()) }
        val intent = Intent(context, NtfyForegroundService::class.java)
        context.stopService(intent)
        if (get(context).isNotBlank()) ContextCompat.startForegroundService(context, intent)
    }

    // ponytail: fixed host, make it a second setting if the server ever moves.
    fun wsUrl(topic: String) = "wss://www.thomaspeoples.com/ntfy/$topic/ws"
}
