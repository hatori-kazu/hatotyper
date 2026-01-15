package com.hatori.hatotyper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Mapping(
    val id: String,
    val trigger: String,
    val output: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)

object MappingStorage {
    private const val PREF = "mapping_prefs"
    private const val KEY_MAPPINGS = "mappings_json"

    private fun getPrefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun loadAll(ctx: Context): List<Mapping> {
        val raw = getPrefs(ctx).getString(KEY_MAPPINGS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Mapping>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id", UUID.randomUUID().toString())
            val trigger = o.optString("trigger", "")
            val output = o.optString("output", "")
            val enabled = o.optBoolean("enabled", true)
            val priority = o.optInt("priority", 0)
            if (trigger.isNotEmpty()) {
                list.add(Mapping(id, trigger, output, enabled, priority))
            }
        }
        return list.sortedWith(compareByDescending<Mapping> { it.priority })
    }

    fun saveAll(ctx: Context, mappings: List<Mapping>) {
        val arr = JSONArray()
        for (m in mappings) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("trigger", m.trigger)
            o.put("output", m.output)
            o.put("enabled", m.enabled)
            o.put("priority", m.priority)
            arr.put(o)
        }
        getPrefs(ctx).edit().putString(KEY_MAPPINGS, arr.toString()).apply()
    }

    fun add(ctx: Context, trigger: String, output: String, enabled: Boolean = true, priority: Int = 0): Mapping {
        val list = loadAll(ctx).toMutableList()
        val m = Mapping(UUID.randomUUID().toString(), trigger, output, enabled, priority)
        list.add(m)
        saveAll(ctx, list)
        return m
    }

    fun update(ctx: Context, id: String, trigger: String, output: String, enabled: Boolean, priority: Int) {
        val list = loadAll(ctx).map {
            if (it.id == id) Mapping(id, trigger, output, enabled, priority) else it
        }
        saveAll(ctx, list)
    }

    fun remove(ctx: Context, id: String) {
        val list = loadAll(ctx).filter { it.id != id }
        saveAll(ctx, list)
    }

    fun setEnabled(ctx: Context, id: String, enabled: Boolean) {
        val list = loadAll(ctx).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveAll(ctx, list)
    }

    fun setPriority(ctx: Context, id: String, priority: Int) {
        val list = loadAll(ctx).map {
            if (it.id == id) it.copy(priority = priority) else it
        }
        saveAll(ctx, list)
    }

    fun clear(ctx: Context) {
        getPrefs(ctx).edit().remove(KEY_MAPPINGS).apply()
    }
}
