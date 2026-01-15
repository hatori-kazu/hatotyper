package com.hatori.hatotyper

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

class OCRProcessor {
    // 日本語認識エンジンを使用するように変更
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    fun processBitmap(bitmap: Bitmap, callback: (String) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                callback(visionText.text)
            }
            .addOnFailureListener { e ->
                callback("")
            }
    }
}
