package com.hatori.hatotyper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class KeyCoord(val x: Float = 0f, val y: Float = 0f)

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

    // --- マッピング（文字置換）用 ---
    fun saveMapping(context: Context, mapping: Mapping) {
        val mappings = getAllMappings(context).toMutableList()
        val index = mappings.indexOfFirst { it.trigger == mapping.trigger }
        if (index != -1) mappings[index] = mapping else mappings.add(mapping)
        saveAll(context, mappings)
    }

    fun getAllMappings(context: Context): List<Mapping> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MAPPINGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Mapping>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun saveAll(context: Context, mappings: List<Mapping>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MAPPINGS, Gson().toJson(mappings)).apply()
    }

    fun deleteMapping(context: Context, trigger: String) {
        val mappings = getAllMappings(context).toMutableList()
        mappings.removeAll { it.trigger == trigger }
        saveAll(context, mappings)
    }

    // --- キャリブレーション（座標保存）用 ---
    fun saveKey(context: Context, char: String, coord: KeyCoord) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val coords = getCoords(context).toMutableMap()
        coords[char] = coord
        prefs.edit().putString(KEY_COORDS, Gson().toJson(coords)).apply()
    }

    fun getCoords(context: Context): Map<String, KeyCoord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_COORDS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, KeyCoord>>() {}.type
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }
}
