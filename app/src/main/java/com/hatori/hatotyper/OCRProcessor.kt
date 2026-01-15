package com.hatori.hatotyper

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.util.Log

class OCRProcessor(private val ctx: Context, private val onTextFound: (String) -> Unit) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val TAG = "OCRProcessor"

    fun processBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text ?: ""
                if (fullText.isNotBlank()) {
                    if (BuildConfig.ENABLE_VERBOSE_LOG) Log.d(TAG, "OCR text: $fullText")
                    onTextFound(fullText)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR failed: ${e.message}")
            }
    }
}
