package com.hatori.hatotyper

import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class CalibrationActivity : AppCompatActivity() {
    companion object {
        private const val REQ_OVERLAY = 2001
    }

    private var overlayView: View? = null
    private lateinit var wm: WindowManager
    private lateinit var infoTv: TextView
    private var capturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        infoTv = findViewById(R.id.tvInfo)
        val btnStart = findViewById<Button>(R.id.btnStartOverlay)
        val btnClear = findViewById<Button>(R.id.btnClear)

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQ_OVERLAY)
                return@setOnClickListener
            }
            startOverlayCapture()
        }

        btnClear.setOnClickListener {
            KeyMapStorage.clear(this)
            updateInfo(getString(R.string.saved_keys_cleared))
        }

        updateInfo()
    }

    private fun updateInfo(msg: String? = null) {
        val m = KeyMapStorage.loadAll(this)
        infoTv.text = (msg ?: "Saved keys: ${m.keys.joinToString(", ")}")
    }

    private fun startOverlayCapture() {
        if (capturing) return
        capturing = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val overlay = object : View(this) {
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    val rawX = ev.rawX
                    val rawY = ev.rawY
                    runOnUiThread {
                        askCharAndSave(rawX, rawY)
                    }
                    return true
                }
                return super.onTouchEvent(ev)
            }
        }

        overlay.setBackgroundColor(0x00000000)
        overlay.isClickable = true
        overlay.isFocusable = true

        overlayView = overlay
        wm.addView(overlayView, params)
        updateInfo(getString(R.string.overlay_started))
    }

    private fun stopOverlay() {
        overlayView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
        capturing = false
        updateInfo()
    }

    private fun askCharAndSave(rawX: Float, rawY: Float) {
        val ed = android.widget.EditText(this)
        ed.hint = getString(R.string.dialog_register_key_hint)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_register_key_title))
            .setMessage(getString(R.string.dialog_register_key_message, rawX.roundToInt(), rawY.roundToInt()))
            .setView(ed)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                val txt = ed.text.toString()
                if (txt.isNotEmpty()) {
                    val key = txt.trim().substring(0, 1)
                    KeyMapStorage.saveKey(this, key, KeyCoord(rawX, rawY))
                    updateInfo("Saved: ${key} => (${rawX.roundToInt()}, ${rawY.roundToInt()})")
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .setNeutralButton(getString(R.string.dialog_done)) { _, _ ->
                stopOverlay()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayCapture()
            } else {
                updateInfo("オーバーレイ許可が必要です。設定で許可してください。")
            }
        }
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }
}
