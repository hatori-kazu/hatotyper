package com.hatori.hatotyper

import androidx.lifecycle.MutableLiveData

object LogManager {
    // ログを保持するLiveData（最大20行）
    val logMessages = MutableLiveData<String>("")
    private val fullLogs = mutableListOf<String>()

    fun appendLog(tag: String, message: String) {
        val newLog = "[$tag] $message"
        fullLogs.add(0, newLog)
        if (fullLogs.size > 20) fullLogs.removeAt(fullLogs.size - 1)
        logMessages.postValue(fullLogs.joinToString("\n"))
    }
}
