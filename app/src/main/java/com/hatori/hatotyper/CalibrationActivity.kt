package com.hatori.hatotyper

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class CalibrationActivity : AppCompatActivity() {
    companion object {
        private const val REQ_OVERLAY = 2001
        private const val CHANNEL_ID = "calibration_channel"
        private const val NOTIFICATION_ID = 100
        private const val ACTION_START_CALIB = "com.hatori.hatotyper.START_CALIB"
    }

    private var overlayView: View? = null
    private lateinit var wm: WindowManager
    private lateinit var infoTv: TextView
    private var capturing = false
    private var isShowingDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        infoTv = findViewById(R.id.tvInfo)
        val btnStart = findViewById<Button>(R.id.btnStartOverlay)
        val btnClear = findViewById<Button>(R.id.btnClear)

        // 通知から起動されたかチェック
        if (intent?.action == ACTION_START_CALIB) {
            // 通知タップ時はオーバーレイを即座に開始し、画面のUI処理（ホーム遷移など）は行わない
            startOverlayCapture()
        }

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQ_OVERLAY)
                return@setOnClickListener
            }
            
            // 1. 通知を表示
            showCalibrationNotification()
            
            // 2. ホーム画面へ移動
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            
            updateInfo("ホームに移動しました。通知から開始してください。")
        }

        btnClear.setOnClickListener {
            updateInfo("座標をクリアしました。")
        }
    }

    // 新しく受け取ったIntent（通知タップ）を処理
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == ACTION_START_CALIB) {
            startOverlayCapture()
        }
    }

    private fun showCalibrationNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Calibration", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        // 通知をタップした時にこのActivityを呼び出すIntent
        val intent = Intent(this, CalibrationActivity::class.java).apply {
            action = ACTION_START_CALIB
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP // 既存のActivityを再利用
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit) // ここをdrawable/ic_launcherに変更可
            .setContentTitle("キャリブレーション準備完了")
            .setContentText("ここをタップして座標の登録を開始してください")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // タップしたら通知を消す
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun startOverlayCapture() {
        if (capturing) return
        capturing = true

        val view = View(this).apply {
            setBackgroundColor(0x22FF0000) 
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
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
    }

    private fun askCharAndSave(rawX: Float, rawY: Float) {
        isShowingDialog = true
        val ed = android.widget.EditText(this).apply { hint = "例: a" }

        AlertDialog.Builder(this)
            .setTitle("文字の登録")
            .setMessage("タップした座標 (${rawX.roundToInt()}, ${rawY.roundToInt()}) に対応する文字を入力してください。")
            .setView(ed)
            .setCancelable(false)
            .setPositiveButton("保存") { _, _ ->
                val txt = ed.text.toString().trim()
                if (txt.isNotEmpty()) {
                    val key = txt.substring(0, 1)
                    KeyMapStorage.saveKey(this, key, KeyCoord(rawX, rawY))
                }
                isShowingDialog = false
            }
            .setNegativeButton("キャンセル") { _, _ -> isShowingDialog = false }
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
    }

    private fun updateInfo(msg: String) {
        infoTv.text = msg
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }
}
