package com.hatori.hatotyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必要に応じてイベントをキャッチ
    }

    override fun onInterrupt() {
        // サービス中断時の処理
    }

    /**
     * 外部（ScreenCaptureServiceなど）から呼び出され、
     * 特定の文字に対応する座標をタップする
     */
    fun tapChar(char: String) {
        val coordsMap = KeyMapStorage.getCoords(this)
        val coord = coordsMap[char] ?: return // 座標が登録されていなければ何もしない

        val path = Path()
        path.moveTo(coord.x, coord.y)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        
        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
            }
        }, null)
    }

    /**
     * 文字列を受け取り、マッピング（置換）を適用した上で順番にタップする
     */
    fun processText(input: String) {
        // 保存されているマッピング（置換ルール）を取得
        val mappings = KeyMapStorage.getAllMappings(this)
        
        var resultText = input
        // 有効なマッピングを優先度順（もしあれば）に適用
        mappings.filter { it.isEnabled }.forEach { map ->
            if (map.trigger.isNotEmpty()) {
                resultText = resultText.replace(map.trigger, map.output)
            }
        }

        // 置換後の文字列を1文字ずつタップ
        resultText.forEach { char ->
            tapChar(char.toString())
            // 連続タップの間に短いスリープが必要な場合はここで制御（要検討）
            Thread.sleep(50) 
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // サービス開始時の初期設定
    }
}
