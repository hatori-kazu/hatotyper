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
            .setContentText("画面をスキャンしています...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        // Android 14+ FGS制約対応
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
                    LogManager.appendLog(TAG, "キャプチャ停止")
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

            // 解像度が高すぎるとOCRが重いため、負荷軽減が必要な場合はここを調整
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // バッファを3確保して安定化
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "HatoCaptureDisplay", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, 
                imageReader?.surface, null, null
            )

            LogManager.appendLog(TAG, "VirtualDisplay作成完了")

            imageReader?.setOnImageAvailableListener({ reader ->
                val currentTime = System.currentTimeMillis()
                
                // 解析間隔（800ms）
                if (currentTime - lastAnalysisTime < 800 || isAnalyzing) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }

                val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
                if (image == null) return@setOnImageAvailableListener

                isAnalyzing = true
                lastAnalysisTime = currentTime

                // Bitmap変換を介してInputImageを作成
                val bitmap = imageToBitmap(image)
                image.close() // Media.Imageは即座に閉じる

                if (bitmap != null) {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            if (visionText.text.isNotEmpty()) {
                                LogManager.appendLog(TAG, "OCR結果: ${visionText.text.take(15)}")
                                processDetectedText(visionText.text)
                            }
                        }
                        .addOnFailureListener { e ->
                            LogManager.appendLog(TAG, "OCR失敗: ${e.message}")
                        }
                        .addOnCompleteListener {
                            isAnalyzing = false
                            // Bitmapのメモリ解放
                            bitmap.recycle()
                        }
                } else {
                    LogManager.appendLog(TAG, "Bitmap変換エラー")
                    isAnalyzing = false
                }
            }, null)

        } catch (e: Exception) {
            LogManager.appendLog(TAG, "起動エラー: ${e.message}")
        }
    }

    /**
     * ImageReaderのRGBA画像をBitmapに変換する
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // 正確なサイズでBitmapを作成
            val bitmap = Bitmap.createBitmap(
