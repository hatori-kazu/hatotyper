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
            .setContentText("フル解像度スキャン実行中...")
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
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    LogManager.appendLog(TAG, "停止: システムにより遮断されました")
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

            // 解像度を下げる処理を廃止し、端末本来の解像度を使用
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            LogManager.appendLog(TAG, "解析開始: ${width}x${height} (フル解像度)")

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "HatoDisplay", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
                
                val currentTime = System.currentTimeMillis()
                // 高解像度でのフリーズを避けるため、解析間隔を1秒に固定
                if (currentTime - lastAnalysisTime < 1000 || isAnalyzing) {
                    image.close()
                    return@setOnImageAvailableListener
                }

                isAnalyzing = true
                lastAnalysisTime = currentTime
                
                LogManager.appendLog(TAG, "--- スキャン実行中 ---")

                val bitmap = imageToBitmap(image)
                image.close()

                if (bitmap != null) {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            if (visionText.text.isNotEmpty()) {
                                // 読み取った全内容をオーバーレイに表示
                                LogManager.appendLog(TAG, "読取: ${visionText.text.replace("\n", " ").take(30)}...")
                                processDetectedText(visionText.text)
                            } else {
                                LogManager.appendLog(TAG, "通知: 文字が検出されませんでした")
                            }
                        }
                        .addOnFailureListener { e ->
                            // エラー内容をすべてオーバーレイへ
                            LogManager.appendLog(TAG, "エラー: ${e.localizedMessage}")
                        }
                        .addOnCompleteListener {
                            isAnalyzing = false
                            bitmap.recycle()
                        }
                } else {
                    LogManager.appendLog(TAG, "エラー: Bitmap変換失敗")
                    isAnalyzing = false
                }
            }, Handler(Looper.getMainLooper()))

        } catch (e: Exception) {
            LogManager.appendLog(TAG, "致命的エラー: ${e.message}")
        }
    }

    private fun processDetectedText(rawText: String) {
        val cleanedText = rawText.replace("\n", "").replace(" ", "").trim()
        val allMappings = KeyMapStorage.getAllMappings(this)
        
        val matchedTriggers = allMappings.filter { it.isEnabled && cleanedText.contains(it.trigger) }
                                         .map { it.trigger }

        if (matchedTriggers.isNotEmpty()) {
            val combinedID = matchedTriggers.joinToString(",")
            LogManager.appendLog(TAG, "一致確認: $combinedID")
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
        LogManager.appendLog(TAG, "サービスを完全に停止しました")
        super.onDestroy()
    }
}
