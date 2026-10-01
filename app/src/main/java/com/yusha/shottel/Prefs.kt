package com.yusha.shottel

import android.content.Context

/** Thin wrapper over SharedPreferences for the handful of settings the app needs. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("shottel", Context.MODE_PRIVATE)

    var botToken: String
        get() = sp.getString("botToken", "") ?: ""
        set(v) = sp.edit().putString("botToken", v.trim()).apply()

    var chatId: String
        get() = sp.getString("chatId", "") ?: ""
        set(v) = sp.edit().putString("chatId", v.trim()).apply()

    /** Interval between screenshots, in seconds. */
    var intervalSeconds: Int
        get() = sp.getInt("intervalSeconds", 300)
        set(v) = sp.edit().putInt("intervalSeconds", v.coerceAtLeast(5)).apply()

    /** JPEG quality 1..100. */
    var jpegQuality: Int
        get() = sp.getInt("jpegQuality", 70)
        set(v) = sp.edit().putInt("jpegQuality", v.coerceIn(1, 100)).apply()

    val isConfigured: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()
}
