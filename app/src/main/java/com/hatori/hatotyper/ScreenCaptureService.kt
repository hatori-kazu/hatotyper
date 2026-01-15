package com.hatori.hatotyper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.Activity
import android.util.Log
import android.os.SystemClock
import android.graphics.Bitmap

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
        ocr = OCRProcessor(this) { recognizedText ->
            val mappings = MappingStorage.loadAll(this).filter { it.enabled }
            var handled = false
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
                            handled = true
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "mapping handle error: ${e.message}")
                    }
                }
            }

            if (!handled) {
                val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
                val target = prefs.getString("targetWord", "") ?: ""
                val input = prefs.getString("inputWord", "") ?: ""
                if (target.isNotEmpty() && recognizedText.contains(target)) {
                    MyAccessibilityService.instance?.let { svc ->
                        if (svc.currentFocusedCanSetText()) {
                            svc.setTextToFocusedField(input)
                        } else {
                            svc.performTapForText(input)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = createNotificationChannel()
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("hatotyper")
            .setContentText("Monitoring screen for target words")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(2, notif)

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

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
                val bitmap = ImageUtil.imageToBitmap(image)
                ocr.processBitmap(bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "image processing error: ${e.message}")
            } finally {
                image.close()
            }
        }, handler)
    }

    private fun createNotificationChannel(): String {
        val channelId = "hatotyper_channel"
        val channelName = "hatotyper"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null) {
            val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
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
