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
            val intent = mpManager.createScreenCaptureIntent()
            startActivityForResult(intent, REQ_MEDIA_PROJ)
        }

        btnGuideAcc.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.guide_acc_title))
                .setMessage(getString(R.string.guide_acc_message))
                .setPositiveButton(getString(R.string.btn_open_accessibility_settings)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNeutralButton(getString(R.string.btn_open_overlay_settings)) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
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
            MyAccessibilityService.instance?.let { svc ->
                if (svc.currentFocusedCanSetText()) {
                    svc.setTextToFocusedField(input)
                } else {
                    svc.performTapForText(input)
                }
            } ?: run {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.guide_acc_title))
                    .setMessage(getString(R.string.accessibility_not_connected))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJ && resultCode == Activity.RESULT_OK && data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java)
            svc.putExtra("resultCode", resultCode)
            svc.putExtra("data", data)
            startForegroundService(svc)
        }
    }
}
