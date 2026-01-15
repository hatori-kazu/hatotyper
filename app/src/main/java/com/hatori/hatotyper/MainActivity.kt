package com.hatori.hatotyper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var logOverlayView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ログ用オーバーレイの作成
        createLogOverlay()

        findViewById<Button>(R.id.btnStartCapture).setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), 1001)
        }

        findViewById<Button>(R.id.btnCalibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        findViewById<Button>(R.id.btnGuideAcc).setOnClickListener {
            startActivity(Settings.ACTION_ACCESSIBILITY_SETTINGS.let { Intent(it) })
        }

        // ログの更新を監視
        LogManager.logMessages.observe(this) { message ->
            logOverlayView.text = message
        }
    }

    private fun createLogOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        logOverlayView = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#AA000000"))
            setTextColor(Color.GREEN)
            textSize = 10f
            setPadding(10, 10, 10, 10)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            400, // ログエリアの高さ
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        if (Settings.canDrawOverlays(this)) {
            wm.addView(logOverlayView, params)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
    }
}
