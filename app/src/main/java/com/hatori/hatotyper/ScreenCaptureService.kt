package com.hatori.hatotyper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private lateinit var ocr: OCRProcessor
    private val TAG = "ScreenCaptureSvc"

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("CaptureThread")
        thread.start()
        handler = Handler(thread.looper)
        
        // OCRプロセッサの初期化
        ocr = OCRProcessor(this) { recognizedText ->
            val mappings = MappingStorage.loadAll(this).filter { it.enabled }
            if (mappings.isNotEmpty()) {
                for (m in mappings) {
                    try {
                        if (recognizedText.contains(m.trigger, ignoreCase = true)) {
                            MyAccessibilityService.instance?.let { svc ->
                                if (svc.currentFocusedCanSetText()) {
                                    svc.setTextToFocusedField(m.output)
                                } else {
                                    svc.performTapForText(m.output)
                                }
                            }
                            break // 1つ一致したら終了（優先度順）
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Mapping processing error: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = createNotificationChannel()
        
        // Android 12対応: FLAG_IMMUTABLE を明示的に指定
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("hatotyper 実行中")
            .setContentText("画面上の文字を監視しています")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()

        // Android 12以降、MediaProjectionを使用する場合は
        // startForegroundをonStartCommandの冒頭で呼ぶ必要があります
        startForeground(1, notification)

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            startCapture()
        }

        return START_STICKY
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // ImageFormat.YUV_420_888 は多くの端末で安定して動作します
        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        mediaProjection?.createVirtualDisplay(
            "ocr-cap",
            width, height, density,
            0,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // ImageUtilを使用してBitmapに変換しOCRを実行
                val bitmap = ImageUtil.imageToBitmap(image)
                ocr.processBitmap(bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "Image processing error: ${e.message}")
            } finally {
                image.close()
            }
        }, handler)
    }

    private fun createNotificationChannel(): String {
        val channelId = "hatotyper_channel"
        val channelName = "Screen Monitoring Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            nm?.createNotificationChannel(channel)
        }
        return channelId
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }
}
