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
            LogManager.appendLog("Capture", "FGS開始失敗")
            return START_NOT_STICKY
        }

        if (data != null && mediaProjection == null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.appendLog("Capture", "セッション停止")
                    stopSelf()
                }
            }, null)
            
            LogManager.appendLog("Capture", "キャプチャセッション開始")
            // セッション開始直後に確実に開始
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            // getRealMetricsを使用して、ナビゲーションバー等を含む正確なサイズを取得
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
            val height = if (metrics.heightPixels > 0) metrics.heightPixels else 1920
            val density = if (metrics.densityDpi > 0) metrics.densityDpi else 440

            LogManager.appendLog("Capture", "解像度: ${width}x${height}")

            // ImageReaderの作成
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            // VirtualDisplayの作成 (クラッシュしやすいポイントをtry-catch)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "HatoCaptureDisplay", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnalysisTime < 600) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                if (isAnalyzing) return@setOnImageAvailableListener
                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                
                isAnalyzing = true
                lastAnalysisTime = currentTime

                val inputImage = try {
                    InputImage.fromMediaImage(image, 0)
                } catch (e: Exception) {
                    image.close()
                    isAnalyzing = false
                    null
                } ?: return@setOnImageAvailableListener

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        if (visionText.text.isNotEmpty()) {
                            LogManager.appendLog("Capture", "認識: ${visionText.text.take(15)}")
                            processDetectedText(visionText.text)
                        }
                    }
                    .addOnFailureListener { e ->
                        LogManager.appendLog("Capture", "OCR失敗: ${e.message}")
                    }
                    .addOnCompleteListener {
                        image.close()
                        isAnalyzing = false
                    }
            }, null)
            
            LogManager.appendLog("Capture", "VirtualDisplay作成完了")

        } catch (e: Exception) {
            LogManager.appendLog("Capture", "起動致命的エラー: ${e.message}")
            Log.e("HatoCapture", "Fatal error in startCapture", e)
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
        super.onDestroy()
    }
}
