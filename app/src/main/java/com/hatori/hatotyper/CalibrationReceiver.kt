package com.hatori.hatotyper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalibrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // アクションが一致するかチェック
        if (intent.action == CalibrationActivity.ACTION_START_OVERLAY) {
            // CalibrationActivityのstaticメソッドを呼び出してオーバーレイを起動
            CalibrationActivity.startOverlayDirectly()
        }
    }
}
