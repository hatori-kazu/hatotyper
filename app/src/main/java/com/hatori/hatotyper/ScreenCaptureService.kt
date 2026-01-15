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
    private lateinit var windowManager: WindowManager

    // OCR Recognizerの初期化
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

        // 1. 通知を表示してフォアグラウンドサービスを開始（Android 14対応）
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ハトタイパー稼働中")
            .setContentText("画面上の文字を自動解析しています...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. MediaProjectionの取得
        if (data != null && mediaProjection == null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // 解析用のImageReader作成 (メモリ節約のためバッファは2つ)
        imageReader = ImageReader.newInstance(
            metrics.widthPixels, 
            metrics.heightPixels, 
            PixelFormat.RGBA_8888, 
            2
        )
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "HatoCaptureDisplay",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val currentTime = System.currentTimeMillis()
            // CPU負荷軽減のため、前回の解析から500ms経過するまでスキップ
            if (currentTime - lastAnalysisTime < 500) {
                val image = reader.acquireLatestImage()
                image?.close()
                return@setOnImageAvailableListener
            }

            if (isAnalyzing) return@setOnImageAvailableListener

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            isAnalyzing = true
            lastAnalysisTime = currentTime

            // ML Kit用のInputImageを作成
            val inputImage = InputImage.fromMediaImage(image, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    // 認識されたテキストがあれば処理
                    if (visionText.text.isNotEmpty()) {
                        processDetectedText(visionText.text)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR解析エラー: ${e.message}")
                }
                .addOnCompleteListener {
                    image.close()
                    isAnalyzing = false
                }
        }, null)
    }

    private fun processDetectedText(rawText: String) {
        // 不要な改行や空白を除去
        val cleanedText = rawText.replace("\n", "").replace(" ", "").trim()
        
        if (cleanedText.isNotEmpty()) {
            Log.d(TAG, "OCR認識結果: $cleanedText")
            
            // アクセシビリティサービスに文字を渡してタップ実行
            MyAccessibilityService.getInstance()?.processText(cleanedText)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
