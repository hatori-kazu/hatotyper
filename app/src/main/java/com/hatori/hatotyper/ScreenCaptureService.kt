package com.hatori.hatotyper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // 日本語認識用エンジン
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    
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
            .setContentTitle("ハトタイパー日本語版")
            .setContentText("画面上の日本語をスキャン中...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (data != null && mediaProjection == null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            // システム側の停止を検知して自動終了
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.appendLog(TAG, "システムのキャプチャが停止されました")
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))
            
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(metrics)

            imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 3)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "HatoDisplay", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnalysisTime < 800 || isAnalyzing) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                isAnalyzing = true
                lastAnalysisTime = currentTime

                val bitmap = imageToBitmap(image)
                image.close()

                if (bitmap != null) {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            processDetectedText(visionText.text)
                        }
                        .addOnCompleteListener {
                            isAnalyzing = false
                            bitmap.recycle()
                        }
                } else {
                    isAnalyzing = false
                }
            }, null)

            LogManager.appendLog(TAG, "日本語OCRスキャン開始")

        } catch (e: Exception) {
            LogManager.appendLog(TAG, "起動エラー: ${e.message}")
        }
    }

    private fun processDetectedText(rawText: String) {
        val cleanedText = rawText.replace("\n", "").replace(" ", "").trim()
        val allMappings = KeyMapStorage.getAllMappings(this)
        
        // ターゲット単語が画面にあるか照合
        val matchedTriggers = allMappings.filter { it.isEnabled && cleanedText.contains(it.trigger) }
                                         .map { it.trigger }

        if (matchedTriggers.isNotEmpty()) {
            val combinedID = matchedTriggers.joinToString(",")
            MyAccessibilityService.getInstance()?.processText(combinedID)
        } else {
            MyAccessibilityService.getInstance()?.processText("")
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            if (rowPadding != 0) Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height) else bitmap
        } catch (e: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        recognizer.close()
        LogManager.appendLog(TAG, "解析サービス停止完了")
        super.onDestroy()
    }
}
