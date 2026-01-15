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

        btnStartCapture.setOnClickListener {
            prefs.edit()
                .putString("targetWord", etTarget.text.toString())
                .putString("inputWord", etInput.text.toString())
                .apply()
            startActivityForResult(mpManager.createScreenCaptureIntent(), REQ_MEDIA_PROJ)
        }

        btnGuideAcc.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("アクセシビリティの設定")
                .setMessage("自動入力を行うために、設定画面で「hatotyper」をONにしてください。")
                .setPositiveButton("設定を開く") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }

        btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        btnManage.setOnClickListener {
            startActivity(Intent(this, MappingListActivity::class.java))
        }

        btnTest.setOnClickListener {
            val input = etInput.text.toString()
            val svc = MyAccessibilityService.instance
            if (svc != null) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJ && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            // Android 12対応: ContextCompatを使用して開始
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }
}
