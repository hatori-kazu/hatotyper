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
    private var emptyCount = 0 // 空文字（未検出）をカウントする変数

    companion object {
        private var instance: MyAccessibilityService? = null
        fun getInstance(): MyAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogManager.appendLog("HatoAcc", "操作サービス準備完了")
    }

    fun processText(matchedID: String) {
        // 1. 文字が検出されなかった場合（空文字）
        if (matchedID.isEmpty()) {
            // すぐにリセットせず、数回連続で空だった場合のみリセットを許可する
            // OCRの一瞬のチラつきで再反応するのを防ぐ
            emptyCount++
            if (emptyCount > 3) { // 約3〜4秒間、文字が見つからなければリセット
                if (lastMatchedID != "") {
                    LogManager.appendLog("HatoAcc", "状態リセット: 文字が消えました")
                    lastMatchedID = ""
                }
            }
            return
        }

        // 2. 文字が検出された場合、空カウントを0に戻す
        emptyCount = 0

        // 3. 重複チェック（前回と全く同じなら無視）
        if (matchedID == lastMatchedID) {
            return
        }
        
        // 4. 新しい文字を認識
        lastMatchedID = matchedID
        LogManager.appendLog("HatoAcc", "新規検知: $matchedID")

        // 実際のマッピング実行
        val allMappings = KeyMapStorage.getAllMappings(this)
        val currentTriggers = matchedID.split(",")

        allMappings.filter { it.isEnabled && currentTriggers.contains(it.trigger) }.forEach { map ->
            LogManager.appendLog("HatoAcc", "実行中: ${map.trigger} -> ${map.output}")
            
            map.output.forEachIndexed { index, char ->
                handler.postDelayed({
                    tapChar(char.toString())
                }, index * 180L) // 間隔を少し広げて安定化
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
