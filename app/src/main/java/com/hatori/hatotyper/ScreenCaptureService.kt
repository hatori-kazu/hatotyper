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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("DATA")

        // 1. まず通知を出し、サービスをフォアグラウンド化（最優先）
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ハトタイパー稼働中")
            .setContentText("画面を解析しています...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            LogManager.appendLog("Capture", "フォアグラウンドサービス開始失敗")
            return START_NOT_STICKY
        }

        // 2. startForegroundを実行した後にのみ、MediaProjectionを取得できる
        if (data != null && mediaProjection == null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            // Android 14以降で必須の登録（キャプチャ停止時の処理）
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.appendLog("Capture", "キャプチャ停止")
                    stopSelf()
                }
            }, null)
            
            LogManager.appendLog("Capture", "キャプチャセッション開始")
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "HatoCaptureDisplay", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnalysisTime < 600) { // 解析負荷軽減
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            if (isAnalyzing) return@setOnImageAvailableListener
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            isAnalyzing = true
            lastAnalysisTime = currentTime

            val inputImage = InputImage.fromMediaImage(image, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    if (visionText.text.isNotEmpty()) {
                        LogManager.appendLog("Capture", "認識: ${visionText.text.take(15)}")
                        processDetectedText(visionText.text)
                    }
                }
                .addOnFailureListener { e ->
                    LogManager.appendLog("Capture", "OCRエラー: ${e.message}")
                }
                .addOnCompleteListener {
                    image.close()
                    isAnalyzing = false
                }
        }, null)
    }

    private fun processDetectedText(rawText: String) {
        val cleaned = rawText.replace("\n", "").replace(" ", "").trim()
        if (cleaned.isNotEmpty()) {
            val service = MyAccessibilityService.getInstance()
            if (service == null) {
                LogManager.appendLog("Capture", "AccサービスがOFFです")
            } else {
                service.processText(cleaned)
            }
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
        super.onDestroy()
    }
}
