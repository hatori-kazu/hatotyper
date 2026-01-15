package com.hatori.hatotyper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private var isAnalyzing = false
    private var lastAnalysisTime = 0L

    companion object {
        private const val NOTIFICATION_ID = 200
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val TAG = "HatoCapture"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("DATA")

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ハトタイパー稼働中")
            .setContentText("画面解析プロセスを実行中...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            LogManager.appendLog(TAG, "FGS開始失敗: ${e.message}")
            return START_NOT_STICKY
        }

        if (data != null && mediaProjection == null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.appendLog(TAG, "投影が停止しました")
                    stopSelf()
                }
            }, null)
            
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
            val height = if (metrics.heightPixels > 0) metrics.heightPixels else 1920
            val density = if (metrics.densityDpi > 0) metrics.densityDpi else 440

            // バッファ数を3に増やして安定化
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "HatoCaptureDisplay", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                imageReader?.surface, null, null
            )

            LogManager.appendLog(TAG, "VirtualDisplay作成完了: 待機中...")

            imageReader?.setOnImageAvailableListener({ reader ->
                val currentTime = System.currentTimeMillis()
                
                // 前回の解析から800ms経過していない場合は破棄して戻る
                if (currentTime - lastAnalysisTime < 800) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                if (isAnalyzing) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                val image = try {
                    reader.acquireLatestImage()
                } catch (e: Exception) {
                    null
                }

                if (image == null) {
                    return@setOnImageAvailableListener
                }

                // 解析フラグON
                isAnalyzing = true
                lastAnalysisTime = currentTime
                
                LogManager.appendLog(TAG, "画像取得: OCR開始")

                val inputImage = try {
                    InputImage.fromMediaImage(image, 0)
                } catch (e: Exception) {
                    LogManager.appendLog(TAG, "InputImage作成失敗")
                    image.close()
                    isAnalyzing = false
                    return@setOnImageAvailableListener
                }

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        if (visionText.text.isNotEmpty()) {
                            LogManager.appendLog(TAG, "OCR結果: ${visionText.text.take(15)}")
                            processDetectedText(visionText.text)
                        } else {
                            // 画面に何も文字がない場合もログを出す（デバッグ用）
                            Log.d(TAG, "文字が検出されませんでした")
                        }
                    }
                    .addOnFailureListener { e ->
                        LogManager.appendLog(TAG, "OCR失敗: ${e.message}")
                    }
                    .addOnCompleteListener {
                        // 確実に画像を閉じ、フラグを戻す
                        image.close()
                        isAnalyzing = false
                        Log.d(TAG, "解析サイクル完了")
                    }
            }, null)

        } catch (e: Exception) {
            LogManager.appendLog(TAG, "致命的エラー: ${e.message}")
        }
    }

    private fun processDetectedText(rawText: String) {
        val cleaned = rawText.replace("\n", "").replace(" ", "").trim()
        if (cleaned.isNotEmpty()) {
            MyAccessibilityService.getInstance()?.processText(cleaned)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        recognizer.close()
        LogManager.appendLog(TAG, "サービス終了")
        super.onDestroy()
    }
}
