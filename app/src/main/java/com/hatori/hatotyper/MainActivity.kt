package com.hatori.hatotyper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var etTarget: EditText
    private lateinit var etInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etTarget = findViewById(R.id.etTarget)
        etInput = findViewById(R.id.etInput)

        findViewById<Button>(R.id.btnStartCapture).setOnClickListener {
            startProjection()
        }

        findViewById<Button>(R.id.btnCalibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        findViewById<Button>(R.id.btnGuideAcc).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnManageMappings).setOnClickListener {
            startActivity(Intent(this, MappingListActivity::class.java))
        }
    }

    private fun startProjection() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // ユーザーに画面キャプチャの許可を求めるダイアログを表示
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("DATA", data)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "キャプチャを開始しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "許可が拒否されました", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
