package com.yusha.shottel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val svc = Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                    putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(CaptureService.EXTRA_RESULT_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, svc)
                status.text = "Running. Captures every 10s while you use the phone."
            } else {
                status.text = "Screen-capture permission was declined."
            }
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val token = findViewById<EditText>(R.id.token)
        val chat = findViewById<EditText>(R.id.chat)
        val start = findViewById<Button>(R.id.start)
        val stop = findViewById<Button>(R.id.stop)
        status = findViewById(R.id.status)

        token.setText(prefs.botToken)
        chat.setText(prefs.chatId)

        start.setOnClickListener {
            prefs.botToken = token.text.toString()
            prefs.chatId = chat.text.toString()
            if (!prefs.isConfigured) {
                Toast.makeText(this, "Enter bot token and chat id first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ensureNotifPermission()
            requestIgnoreBatteryOptimizations()
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        stop.setOnClickListener {
            startService(Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_STOP
            })
            status.text = "Stopped."
        }
    }

    /** Ask the OS to exempt this app from battery optimization so the service survives. */
    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
