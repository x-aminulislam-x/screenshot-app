package com.yusha.shottel

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Uploads a JPEG to a Telegram chat using the Bot API sendPhoto endpoint. */
object Telegram {

    /**
     * Sends [jpegBytes] to [chatId] via bot [token].
     * Returns null on success, or an error message string on failure.
     * Must be called off the main thread.
     */
    fun sendPhoto(token: String, chatId: String, caption: String, jpegBytes: ByteArray): String? {
        val boundary = "----shottel" + System.currentTimeMillis()
        val lineEnd = "\r\n"
        val url = URL("https://api.telegram.org/bot$token/sendPhoto")

        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 40_000
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(conn.outputStream).use { out ->
                fun writeField(name: String, value: String) {
                    out.writeBytes("--$boundary$lineEnd")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd$lineEnd")
                    out.writeBytes(value + lineEnd)
                }

                writeField("chat_id", chatId)
                writeField("caption", caption)

                out.writeBytes("--$boundary$lineEnd")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"photo\"; filename=\"shot.jpg\"$lineEnd"
                )
                out.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")
                out.write(jpegBytes)
                out.writeBytes(lineEnd)
                out.writeBytes("--$boundary--$lineEnd")
                out.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                null
            } else {
                val err = (conn.errorStream ?: conn.inputStream)?.bufferedReader()
                    ?.use { it.readText() } ?: ""
                "HTTP $code: $err"
            }
        } catch (e: Exception) {
            e.message ?: "unknown network error"
        } finally {
            conn?.disconnect()
        }
    }
}
