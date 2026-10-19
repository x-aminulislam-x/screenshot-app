package com.yusha.shottel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that, every [Prefs.INTERVAL_SECONDS] seconds, captures one screen
 * frame and uploads it to Telegram. Capture pauses automatically while the device is
 * idle (screen off / not interactive) and resumes when the user is active again.
 */
class CaptureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        private const val CHANNEL_ID = "capture"
        private const val NOTIF_ID = 1
        private const val TAG = "CaptureService"

        @Volatile var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var powerManager: PowerManager
    private var deviceName: String = "Device"

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var bgThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    private var width = 0
    private var height = 0
    private var density = 0

    private val tickHandler = Handler(android.os.Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            // Capture only while the device is interactive; pause silently when idle.
            if (powerManager.isInteractive) {
                captureAndSend()
            }
            tickHandler.postDelayed(this, Config.INTERVAL_SECONDS * 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val sp = getSharedPreferences("shottel", Context.MODE_PRIVATE)
        deviceName = sp.getString("deviceName", "Device")?.ifBlank { "Device" } ?: "Device"
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_START -> startCapture(intent)
            else -> {
                // Restarted by the system after a kill; the capture grant cannot be
                // restored silently, so stay foreground and ask the user to resume.
                startForeground(NOTIF_ID, buildResumeNotification())
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app from Recents — keep capturing.
        super.onTaskRemoved(rootIntent)
    }

    private fun startCapture(intent: Intent) {
        // Must be foreground (mediaProjection type) BEFORE obtaining the projection.
        startForeground(NOTIF_ID, buildNotification("Kulumbi is running"))

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == Int.MIN_VALUE || data == null) {
            Log.e(TAG, "Missing projection result; stopping.")
            stopEverything()
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        if (projection == null) {
            stopEverything()
            return
        }

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "Projection stopped by system/user.")
                stopEverything()
            }
        }, tickHandler)

        computeMetrics()
        setupVirtualDisplay()

        isRunning = true
        tickHandler.post(tickRunnable)
    }

    private fun computeMetrics() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
    }

    private fun setupVirtualDisplay() {
        bgThread = HandlerThread("capture-bg").also { it.start() }
        bgHandler = Handler(bgThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "kulumbi",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            bgHandler
        )
    }

    private fun captureAndSend() {
        val reader = imageReader ?: return
        bgHandler?.post {
            val bitmap = grabBitmap(reader) ?: run {
                Log.w(TAG, "No frame available this tick.")
                return@post
            }
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, Config.JPEG_QUALITY, baos)
            bitmap.recycle()
            val bytes = baos.toByteArray()

            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val caption = "$deviceName • $stamp"
            scope.launch {
                Telegram.sendPhoto(Config.BOT_TOKEN, Config.CHAT_ID, caption, bytes)
            }
        }
    }

    private fun grabBitmap(reader: ImageReader): Bitmap? {
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "grabBitmap failed", e)
            null
        } finally {
            image.close()
        }
    }

    private fun stopEverything() {
        isRunning = false
        tickHandler.removeCallbacks(tickRunnable)
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        projection = null
        bgThread?.quitSafely()
        bgThread = null
        bgHandler = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    // --- Notification plumbing ---

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID, "Kulumbi", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Screen capture status" }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kulumbi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    /** Shown after a system kill/restart, when the capture grant must be re-approved. */
    private fun buildResumeNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Kulumbi")
            .setContentText("Capture was interrupted. Tap to resume.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
