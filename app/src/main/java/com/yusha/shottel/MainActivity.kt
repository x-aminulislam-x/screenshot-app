package com.yusha.shottel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
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
                status.text = "Capturing. You can leave the app open in the background."
            } else {
                status.text = "Screen-capture permission was declined."
            }
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val token = findViewById<EditText>(R.id.token)
        val chat = findViewById<EditText>(R.id.chat)
        val interval = findViewById<EditText>(R.id.interval)
        val quality = findViewById<EditText>(R.id.quality)
        val save = findViewById<Button>(R.id.save)
        val start = findViewById<Button>(R.id.start)
        val stop = findViewById<Button>(R.id.stop)
        status = findViewById(R.id.status)

        token.setText(prefs.botToken)
        chat.setText(prefs.chatId)
        interval.setText(prefs.intervalSeconds.toString())
        quality.setText(prefs.jpegQuality.toString())

        save.setOnClickListener {
            prefs.botToken = token.text.toString()
            prefs.chatId = chat.text.toString()
            prefs.intervalSeconds = interval.text.toString().toIntOrNull() ?: 300
            prefs.jpegQuality = quality.text.toString().toIntOrNull() ?: 70
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        start.setOnClickListener {
            prefs.botToken = token.text.toString()
            prefs.chatId = chat.text.toString()
            prefs.intervalSeconds = interval.text.toString().toIntOrNull() ?: 300
            prefs.jpegQuality = quality.text.toString().toIntOrNull() ?: 70

            if (!prefs.isConfigured) {
                Toast.makeText(this, "Enter bot token and chat id first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            ensureNotifPermission()
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        stop.setOnClickListener {
            val svc = Intent(this, CaptureService::class.java).apply {
                action = CaptureService.ACTION_STOP
            }
            startService(svc)
            status.text = "Stopped."
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
