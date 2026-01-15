package com.hatori.hatotyper

import android.content.Context
import org.json.JSONObject

data class KeyCoord(val x: Float, val y: Float)

object KeyMapStorage {
    private const val PREF = "keymap_prefs"
    private const val KEY_MAP = "key_map_json"

    fun saveKey(context: Context, char: String, coord: KeyCoord) {
        if (char.isEmpty()) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = JSONObject(prefs.getString(KEY_MAP, "{}") ?: "{}")
        val o = JSONObject()
        o.put("x", coord.x)
        o.put("y", coord.y)
        json.put(char, o)
        prefs.edit().putString(KEY_MAP, json.toString()).apply()
    }

    fun loadAll(context: Context): Map<String, KeyCoord> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MAP, "{}") ?: "{}"
        val json = JSONObject(raw)
        val keys = mutableMapOf<String, KeyCoord>()
        val it = json.keys()
        while (it.hasNext()) {
            val k = it.next()
            val o = json.getJSONObject(k)
            val x = o.getDouble("x").toFloat()
            val y = o.getDouble("y").toFloat()
            keys[k] = KeyCoord(x, y)
        }
        return keys
    }

    fun getCoord(context: Context, char: String): KeyCoord? {
        return loadAll(context)[char]
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_MAP).apply()
    }
}
