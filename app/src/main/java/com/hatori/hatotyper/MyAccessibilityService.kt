package com.hatori.hatotyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastMatchedID: String = ""

    companion object {
        private var instance: MyAccessibilityService? = null
        fun getInstance(): MyAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    fun processText(matchedID: String) {
        // 1. 重複チェック（画面から消えるまで再反応しない）
        if (matchedID == lastMatchedID) return
        
        lastMatchedID = matchedID
        if (matchedID.isEmpty()) return

        // 2. 実際のマッピング内容に基づきタップ実行
        val allMappings = KeyMapStorage.getAllMappings(this)
        val currentTriggers = matchedID.split(",")

        allMappings.filter { it.isEnabled && currentTriggers.contains(it.trigger) }.forEach { map ->
            LogManager.appendLog("HatoAcc", "実行中: ${map.trigger} -> ${map.output}")
            
            map.output.forEachIndexed { index, char ->
                handler.postDelayed({
                    tapChar(char.toString())
                }, index * 150L)
            }
        }
    }

    private fun tapChar(char: String) {
        val coordsMap = KeyMapStorage.getCoords(this)
        val coord = coordsMap[char] ?: return

        val path = Path().apply { moveTo(coord.x, coord.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { instance = null }
    override fun onDestroy() { instance = null; super.onDestroy() }
}
