package com.hatori.hatotyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.*
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastMatchedID: String = ""
    private var emptyCount = 0 

    // バイブレーターの取得
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

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
        if (matchedID.isEmpty()) {
            emptyCount++
            if (emptyCount >= 3) {
                if (lastMatchedID != "") {
                    LogManager.appendLog("HatoAcc", "消失を確認: リセット")
                    lastMatchedID = ""
                }
            }
            return
        }

        emptyCount = 0
        if (matchedID == lastMatchedID) return

        lastMatchedID = matchedID
        LogManager.appendLog("HatoAcc", "検知: $matchedID")

        // ★バイブレーションを実行（検知した瞬間に1回震わせる）
        triggerVibration()

        executeMappings(matchedID)
    }

    private fun triggerVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 短く「ブッ」と2回震わせるパターン (待機0ms, 振動100ms, 待機50ms, 振動100ms)
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200) // 旧バージョン用
        }
    }

    private fun executeMappings(matchedID: String) {
        val allMappings = KeyMapStorage.getAllMappings(this)
        val currentTriggers = matchedID.split(",")

        allMappings.filter { it.isEnabled && currentTriggers.contains(it.trigger) }.forEach { map ->
            LogManager.appendLog("HatoAcc", "実行: [${map.trigger}]")
            
            map.output.forEachIndexed { index, char ->
                handler.postDelayed({
                    tapChar(char.toString())
                }, index * 200L)
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
