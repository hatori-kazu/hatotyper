package com.hatori.hatotyper

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        const val ACTION_START_OVERLAY = "com.hatori.hatotyper.ACTION_START_OVERLAY"
        
        // Activityが生存している間、外部（Receiver）からアクセス可能にするためのインスタンス保持
        private var instance: CalibrationActivity? = null
        
        fun startOverlayDirectly() {
            instance?.triggerOverlayFromReceiver()
        }
    }

    private var overlayView: View? = null
    private lateinit var wm: WindowManager
    private lateinit var infoTv: TextView
    private var capturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)
        instance = this

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        infoTv = findViewById(R.id.tvInfo)
        val btnStart = findViewById<Button>(R.id.btnStartOverlay)
        val btnClear = findViewById<Button>(R.id.btnClear)

        btnStart.setOnClickListener {
            prepareNextCapture("ホームに移動しました。通知から座標選択を開始してください。")
        }

        btnClear.setOnClickListener {
            updateInfo("座標をクリアしました。")
            // KeyMapStorageのクリア処理等
        }
    }

    private fun prepareNextCapture(message: String) {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQ_OVERLAY)
            return
        }
        
        showCalibrationNotification()
        
        // ホーム画面へ移動
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        updateInfo(message)
    }

    private fun showCalibrationNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Calibration", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        // 画面を開かず、BroadcastReceiverを呼び出す設定
        val intent = Intent(this, CalibrationReceiver::class.java).apply {
            action = ACTION_START_OVERLAY
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("座標を選択してください")
            .setContentText("ここをタップしてオーバーレイを表示")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    fun triggerOverlayFromReceiver() {
        runOnUiThread {
            startOverlayCapture()
        }
    }

    private fun startOverlayCapture() {
        if (capturing) return
        capturing = true

        val view = View(this).apply {
            setBackgroundColor(0x22FF0000) 
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val tx = event.rawX
                    val ty = event.rawY
                    stopOverlay() // タップされたら即座に消去
                    showRegisterDialog(tx, ty)
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

    private fun showRegisterDialog(rawX: Float, rawY: Float) {
        // ダイアログを表示するためにActivityを前面に出す
        val intent = Intent(this, CalibrationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(intent)

        val ed = android.widget.EditText(this).apply { hint = "例: a" }

        AlertDialog.Builder(this)
            .setTitle("座標の登録")
            .setMessage("座標: (${rawX.roundToInt()}, ${rawY.roundToInt()})")
            .setView(ed)
            .setCancelable(false)
            .setPositiveButton("保存して次へ") { _, _ ->
                val txt = ed.text.toString().trim()
                if (txt.isNotEmpty()) {
                    KeyMapStorage.saveKey(this, txt.substring(0, 1), KeyCoord(rawX, rawY))
                }
                prepareNextCapture("次の文字の準備ができました（通知から開始）。")
            }
            .setNeutralButton("座標を再選択") { _, _ ->
                prepareNextCapture("座標を選択し直してください（通知から開始）。")
            }
            .setNegativeButton("終了", null)
            .show()
    }

    private fun stopOverlay() {
        overlayView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
        capturing = false
    }

    private fun updateInfo(msg: String) {
        infoTv.text = msg
    }

    override fun onDestroy() {
        stopOverlay()
        instance = null
        super.onDestroy()
    }
}
