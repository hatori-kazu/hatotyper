package com.hatori.hatotyper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    
    private var isAnalyzing = false
    private var lastAnalysisTime = 0L

    // 指定座標：左 90, 上 500, 右 1350, 下 950
    private val scanRect = Rect(90, 500, 1350, 950)

    companion object {
        private const val TAG = "HatoCapture"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 200
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("DATA") ?: return START_NOT_STICKY

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ハトタイパー稼働中")
            .setContentText("範囲スキャンとバイブ検知が有効です")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (mediaProjection == null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.appendLog(TAG, "停止: プロジェクション終了")
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
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnalysisTime < 1000 || isAnalyzing) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                isAnalyzing = true
                lastAnalysisTime = currentTime

                val fullBitmap = imageToBitmap(image)
                image.close()

                if (fullBitmap != null) {
                    // 範囲切り抜き
                    val cropX = scanRect.left.coerceIn(0, fullBitmap.width - 1)
                    val cropY = scanRect.top.coerceIn(0, fullBitmap.height - 1)
                    val cropW = scanRect.width().coerceAtMost(fullBitmap.width - cropX)
                    val cropH = scanRect.height().coerceAtMost(fullBitmap.height - cropY)

                    if (cropW > 0 && cropH > 0) {
                        val croppedBitmap = Bitmap.createBitmap(fullBitmap, cropX, cropY, cropW, cropH)
                        fullBitmap.recycle()

                        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                processDetectedText(visionText.text)
                            }
                            .addOnFailureListener { e ->
                                LogManager.appendLog(TAG, "OCRエラー: ${e.localizedMessage}")
                            }
                            .addOnCompleteListener {
                                isAnalyzing = false
                                croppedBitmap.recycle()
                            }
                    } else {
                        fullBitmap.recycle()
                        isAnalyzing = false
                    }
                } else {
                    isAnalyzing = false
                }
            }, Handler(Looper.getMainLooper()))

            LogManager.appendLog(TAG, "スキャン開始: 座標限定モード")
        } catch (e: Exception) {
            LogManager.appendLog(TAG, "起動エラー: ${e.localizedMessage}")
        }
    }

    private fun processDetectedText(rawText: String) {
        val cleanedText = rawText.replace("\n", "").replace(" ", "").trim()
        val allMappings = KeyMapStorage.getAllMappings(this)
        
        val matchedTriggers = allMappings.filter { it.isEnabled && cleanedText.contains(it.trigger) }
                                         .map { it.trigger }

        if (matchedTriggers.isNotEmpty()) {
            val combinedID = matchedTriggers.joinToString(",")
            LogManager.appendLog(TAG, "検知: $combinedID")
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
            bitmap
        } catch (e: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        recognizer.close()
        super.onDestroy()
    }
}
