package com.hatori.hatotyper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQ_MEDIA_PROJ = 1001
    }

    private lateinit var mpManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val etTarget = findViewById<EditText>(R.id.etTarget)
        val etInput = findViewById<EditText>(R.id.etInput)
        val btnStartCapture = findViewById<Button>(R.id.btnStartCapture)
        val btnGuideAcc = findViewById<Button>(R.id.btnGuideAcc)
        val btnCalib = findViewById<Button>(R.id.btnCalibration)
        val btnTest = findViewById<Button>(R.id.btnTestInput)
        val btnManage = findViewById<Button>(R.id.btnManageMappings)

        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        etTarget.setText(prefs.getString("targetWord", ""))
        etInput.setText(prefs.getString("inputWord", ""))

        // キャプチャ開始ボタン
        btnStartCapture.setOnClickListener {
            // 設定を保存
            prefs.edit()
                .putString("targetWord", etTarget.text.toString())
                .putString("inputWord", etInput.text.toString())
                .apply()

            // メディアプロジェクションの許可ダイアログを表示
            startActivityForResult(mpManager.createScreenCaptureIntent(), REQ_MEDIA_PROJ)
        }

        // アクセシビリティサービスの有効化ガイド
        btnGuideAcc.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.guide_acc_title))
                .setMessage(getString(R.string.guide_acc_message))
                .setPositiveButton("設定を開く") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        // キャリブレーション画面へ
        btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        // マッピング管理画面へ
        btnManage.setOnClickListener {
            startActivity(Intent(this, MappingListActivity::class.java))
        }

        // テスト入力ボタン
        btnTest.setOnClickListener {
            val input = etInput.text.toString()
            val svc = MyAccessibilityService.instance
            if (svc != null) {
                // フォーカスがある場合はテキストをセット、ない場合は座標タップを試行
                if (svc.currentFocusedCanSetText()) {
                    svc.setTextToFocusedField(input)
                } else {
                    svc.performTapForText(input)
                }
            } else {
                AlertDialog.Builder(this)
                    .setTitle("サービス未有効")
                    .setMessage("アクセシビリティサービスを有効にしてください。")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // Android 12対応: 許可を得た直後にフォアグラウンドサービスを開始する
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJ) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
                
                // ContextCompatを使用して安全にフォアグラウンドサービスを開始
                // これにより Android 12 の開始制限を回避しやすくなります
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                // ユーザーがキャンセルした場合などの処理
                AlertDialog.Builder(this)
                    .setMessage("画面キャプチャが許可されませんでした。")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
