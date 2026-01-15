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
        private const val TAG = "HatoAccessibility"

        // ScreenCaptureServiceからインスタンスを取得するためのメソッド
        fun getInstance(): MyAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 特定のイベント処理が必要な場合はここに記述
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * 文字列を処理し、マッピング適用後に各文字をタップする
     */
    fun processText(input: String) {
        // 1. 置換ルールの適用
        val mappings = KeyMapStorage.getAllMappings(this)
        var resultText = input

        mappings.filter { it.isEnabled }.forEach { map ->
            if (map.trigger.isNotEmpty()) {
                resultText = resultText.replace(map.trigger, map.output)
            }
        }

        Log.d(TAG, "Processing text: original=$input, mapped=$resultText")

        // 2. 1文字ずつタップ実行
        // 文字列が長い場合のスタックオーバーフローを防ぐため、シーケンシャルに処理
        resultText.forEachIndexed { index, char ->
            // タップ間にわずかなディレイ（50ms〜）を設ける
            handler.postDelayed({
                tapChar(char.toString())
            }, index * 100L) // 100ms間隔で順次タップ
        }
    }

    /**
     * 指定された1文字に対応する座標をタップする
     */
    private fun tapChar(char: String) {
        val coordsMap = KeyMapStorage.getCoords(this)
        val coord = coordsMap[char]

        if (coord != null) {
            Log.d(TAG, "Tapping '$char' at (${coord.x}, ${coord.y})")
            
            val path = Path()
            path.moveTo(coord.x, coord.y)

            val gestureBuilder = GestureDescription.Builder()
            // 期間50msの短いタップを実行
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            
            dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "Gesture cancelled for char: $char")
                    super.onCancelled(gestureDescription)
                }
            }, null)
        } else {
            Log.w(TAG, "No coordinates found for char: $char")
        }
    }
}
