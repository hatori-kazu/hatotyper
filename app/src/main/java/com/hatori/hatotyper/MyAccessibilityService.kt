package com.hatori.hatotyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {
    companion object {
        var instance: MyAccessibilityService? = null
        private const val TAG = "MyAccService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (BuildConfig.ENABLE_VERBOSE_LOG) Log.i(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun currentFocusedCanSetText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return focus != null
    }

    fun setTextToFocusedField(text: String) {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    fun performTapForText(text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires API 24+")
            return
        }
        if (text.isEmpty()) return

        val keyMap = KeyMapStorage.loadAll(this)
        if (keyMap.isEmpty()) {
            Log.w(TAG, "No keymap saved")
            return
        }

        val gestureBuilder = GestureDescription.Builder()
        var startTime: Long = 0
        val tapDuration = 50L
        val interDelay = 120L

        text.forEach { ch ->
            val key = ch.toString()
            val coord = keyMap[key]
            if (coord == null) {
                Log.w(TAG, "No coord for char='$key', skipping")
                startTime += tapDuration + interDelay
                return@forEach
            }
            val path = Path().apply { moveTo(coord.x, coord.y) }
            val stroke = GestureDescription.StrokeDescription(path, startTime, tapDuration)
            gestureBuilder.addStroke(stroke)
            startTime += tapDuration + interDelay
        }

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "Gesture completed for text='$text'")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture cancelled")
            }
        }, null)
    }
}
