package com.hatori.hatotyper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// 座標保持用のデータクラス（デフォルト値を設定してGsonの解析エラーを防ぐ）
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
    private const val KEY_COORDS = "coords"

    // --- マッピング（文字置換ルール）用 ---

    fun saveMapping(context: Context, mapping: Mapping) {
        val mappings = getAllMappings(context).toMutableList()
        // 同じトリガーがあれば上書き、なければ追加
        val index = mappings.indexOfFirst { it.trigger == mapping.trigger }
        if (index != -1) {
            mappings[index] = mapping
        } else {
            mappings.add(mapping)
        }
        saveAllMappings(context, mappings)
    }

    fun getAllMappings(context: Context): List<Mapping> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MAPPINGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Mapping>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAllMappings(context: Context, mappings: List<Mapping>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(mappings)
        prefs.edit().putString(KEY_MAPPINGS, json).apply()
    }

    fun deleteMapping(context: Context, trigger: String) {
        val mappings = getAllMappings(context).toMutableList()
        mappings.removeAll { it.trigger == trigger }
        saveAllMappings(context, mappings)
    }

    // --- キャリブレーション（座標データ）用 ---

    fun saveKey(context: Context, char: String, coord: KeyCoord) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val coords = getCoords(context).toMutableMap()
        coords[char] = coord
        val json = Gson().toJson(coords)
        prefs.edit().putString(KEY_COORDS, json).apply()
    }

    fun getCoords(context: Context): Map<String, KeyCoord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_COORDS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, KeyCoord>>() {}.type
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 特定のキー座標を個別に削除
     */
    fun deleteKey(context: Context, char: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val coords = getCoords(context).toMutableMap()
        coords.remove(char)
        val json = Gson().toJson(coords)
        prefs.edit().putString(KEY_COORDS, json).apply()
    }

    /**
     * すべての座標データを削除
     */
    fun clearCoords(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_COORDS).apply()
    }
}
