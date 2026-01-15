package com.hatori.hatotyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private var instance: MyAccessibilityService? = null
        private const val TAG = "HatoAcc"
        fun getInstance(): MyAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogManager.appendLog(TAG, "サービス接続完了")
    }

    override fun onInterrupt() { instance = null }
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun processText(input: String) {
        val mappings = KeyMapStorage.getAllMappings(this)
        var resultText = input
        mappings.filter { it.isEnabled }.forEach { map ->
            if (map.trigger.isNotEmpty()) resultText = resultText.replace(map.trigger, map.output)
        }

        LogManager.appendLog(TAG, "入力開始: $resultText")

        resultText.forEachIndexed { index, char ->
            handler.postDelayed({ tapChar(char.toString()) }, index * 100L)
        }
    }

    private fun tapChar(char: String) {
        val coordsMap = KeyMapStorage.getCoords(this)
        val coord = coordsMap[char]

        if (coord != null) {
            LogManager.appendLog(TAG, "タップ: '$char' (x:${coord.x.toInt()}, y:${coord.y.toInt()})")
            val path = Path().apply { moveTo(coord.x, coord.y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
            
            dispatchGesture(gesture, null, null)
        } else {
            LogManager.appendLog(TAG, "未登録キー: '$char'")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
}
