package com.yusha.shottel

import android.content.Context

/** Minimal settings: just the Telegram bot token and target chat id. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("shottel", Context.MODE_PRIVATE)

    var botToken: String
        get() = sp.getString("botToken", "") ?: ""
        set(v) = sp.edit().putString("botToken", v.trim()).apply()

    var chatId: String
        get() = sp.getString("chatId", "") ?: ""
        set(v) = sp.edit().putString("chatId", v.trim()).apply()

    val isConfigured: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()

    companion object {
        /** Fixed capture interval (seconds). */
        const val INTERVAL_SECONDS = 10

        /** JPEG quality for screenshots. */
        const val JPEG_QUALITY = 70
    }
}
