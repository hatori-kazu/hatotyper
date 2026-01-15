package com.hatori.hatotyper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 座標保持用のデータクラス
data class KeyCoord(val x: Float = 0f, val y: Float = 0f)

// マッピング情報のデータクラス
data class Mapping(
    var trigger: String = "",
    var output: String = "",
    var isEnabled: Boolean = true,
    var priority: Int = 0
)

object KeyMapStorage {
    private const val PREF_NAME = "key_map_prefs"
    private const val KEY_MAPPINGS = "mappings"

    fun saveMapping(context: Context, mapping: Mapping) {
        val mappings = getAllMappings(context).toMutableList()
        // 同じトリガーがあれば上書き、なければ追加
        val index = mappings.indexOfFirst { it.trigger == mapping.trigger }
        if (index != -1) {
            mappings[index] = mapping
        } else {
            mappings.add(mapping)
        }
        saveAll(context, mappings)
    }

    fun getAllMappings(context: Context): List<Mapping> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MAPPINGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Mapping>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList() // 解析エラー時は空を返してクラッシュを防ぐ
        }
    }

    fun saveAll(context: Context, mappings: List<Mapping>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(mappings)
        prefs.edit().putString(KEY_MAPPINGS, json).apply()
    }

    fun deleteMapping(context: Context, trigger: String) {
        val mappings = getAllMappings(context).toMutableList()
        mappings.removeAll { it.trigger == trigger }
        saveAll(context, mappings)
    }
}
