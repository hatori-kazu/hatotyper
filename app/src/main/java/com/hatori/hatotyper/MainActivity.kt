package com.hatori.hatotyper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var logOverlayView: TextView? = null
    private var isLogVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 権限確認とログ窓の初期準備
        checkOverlayPermission()

        // 2. ボタン設定
        findViewById<Button>(R.id.btnStartCapture).setOnClickListener {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), 1001)
        }

        findViewById<Button>(R.id.btnToggleLog).setOnClickListener {
            toggleLogOverlay()
        }

        findViewById<Button>(R.id.btnManageMappings).setOnClickListener {
            startActivity(Intent(this, MappingListActivity::class.java))
        }

        findViewById<Button>(R.id.btnCalibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        // 3. ログの購読
        LogManager.logMessages.observe(this) { message ->
            logOverlayView?.text = message
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1002)
        } else {
            createLogOverlay()
        }
    }

    private fun createLogOverlay() {
        if (logOverlayView != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        logOverlayView = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.GREEN)
            textSize = 12f
            setPadding(20, 20, 20, 20)
            text = "ログ待機中..."
            visibility = if (isLogVisible) View.VISIBLE else View.GONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            350,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        try {
            wm.addView(logOverlayView, params)
        } catch (e: Exception) {
            Toast.makeText(this, "オーバーレイ表示失敗", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleLogOverlay() {
        isLogVisible = !isLogVisible
        logOverlayView?.visibility = if (isLogVisible) View.VISIBLE else View.GONE
        val status = if (isLogVisible) "表示" else "非表示"
        Toast.makeText(this, "ログを${status}にしました", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("DATA", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } else if (requestCode == 1002 && Settings.canDrawOverlays(this)) {
            createLogOverlay()
        }
    }
}
