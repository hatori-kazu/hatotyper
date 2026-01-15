package com.hatori.hatotyper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CalibrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CalibrationActivity.ACTION_START_OVERLAY) {
            // 現在のCalibrationActivityのインスタンスを通じてオーバーレイを開始
            // 注意: Activityが既に裏で生きている前提です
            val activity = (context.applicationContext as? HatotyperApp)?.currentActivity as? CalibrationActivity
            activity?.triggerOverlayFromReceiver()
        }
    }
}
