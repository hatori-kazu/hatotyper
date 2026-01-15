package com.hatori.hatotyper

import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
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
    
    // 追加: ダイアログ表示中フラグ（重複防止用）
    private var isShowingDialog = false

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
            // 保存済み座標をクリアする処理（KeyMapStorageに実装がある場合）
            updateInfo("座標をクリアしました。")
        }
    }

    private fun startOverlayCapture() {
        if (capturing) return
        capturing = true

        val view = View(this).apply {
            // 背景を薄い半透明にして、どこをタップしているか分かりやすくする
            setBackgroundColor(0x22FF0000) 
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    // ダイアログ表示中でなければ処理を開始
                    if (!isShowingDialog) {
                        askCharAndSave(event.rawX, event.rawY)
                    }
                }
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        wm.addView(view, params)
        overlayView = view
        updateInfo("キーボードの文字の上を順番にタップしてください。")
    }

    private fun askCharAndSave(rawX: Float, rawY: Float) {
        // ダイアログを表示開始するのでフラグを立てる
        isShowingDialog = true

        val ed = android.widget.EditText(this).apply {
            hint = "例: a"
        }

        AlertDialog.Builder(this)
            .setTitle("文字の登録")
            .setMessage("タップした座標 (${rawX.roundToInt()}, ${rawY.roundToInt()}) に対応する文字を入力してください。")
            .setView(ed)
            .setCancelable(false) // 枠外タップで消えないようにする
            .setPositiveButton("保存") { _, _ ->
                val txt = ed.text.toString().trim()
                if (txt.isNotEmpty()) {
                    val key = txt.substring(0, 1)
                    KeyMapStorage.saveKey(this, key, KeyCoord(rawX, rawY))
                    updateInfo("保存完了: $key")
                }
                // 処理が終わったので次のタップを許可
                isShowingDialog = false
            }
            .setNegativeButton("キャンセル") { _, _ ->
                // キャンセル時もフラグを戻す
                isShowingDialog = false
            }
            .setNeutralButton("終了") { _, _ ->
                stopOverlay()
                isShowingDialog = false
            }
            .show()
    }

    private fun stopOverlay() {
        overlayView?.let {
            wm.removeView(it)
            overlayView = null
        }
        capturing = false
        updateInfo("キャリブレーションを終了しました。")
    }

    private fun updateInfo(msg: String) {
        infoTv.text = msg
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }
}
