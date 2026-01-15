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
            .setContentText("マッピング照合を実行しています...")
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
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnalysisTime < 700 || isAnalyzing) {
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
        } catch (e: Exception) {
            LogManager.appendLog(TAG, "起動エラー: ${e.message}")
        }
    }

    private fun processDetectedText(rawText: String) {
        val cleanedText = rawText.replace("\n", "").replace(" ", "").trim()
        val allMappings = KeyMapStorage.getAllMappings(this)
        
        // 画面内にある「有効なマッピングのトリガー」だけを抽出
        val matchedTriggers = allMappings.filter { it.isEnabled && cleanedText.contains(it.trigger) }
                                         .map { it.trigger }

        if (matchedTriggers.isNotEmpty()) {
            val combinedID = matchedTriggers.joinToString(",")
            MyAccessibilityService.getInstance()?.processText(combinedID)
        } else {
            // 何も見つからない場合は空文字を送ってAcc側の状態をリセット
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
        virtualDisplay?.release(); mediaProjection?.stop(); imageReader?.close(); recognizer.close()
        super.onDestroy()
    }
}
