package com.notkrishenough.cadence.data

import android.content.Context
import com.notkrishenough.cadence.model.Track
import org.json.JSONArray
import org.json.JSONObject

class CadenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("cadence", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> = prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun toggleFavorite(id: Long): Set<Long> {
        val next = favorites().toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet("favorites", next.map(Long::toString).toSet()).apply()
        return next
    }

    fun playlists(): Map<String, List<Long>> {
        val raw = prefs.getString("playlists", "{}") ?: "{}"
        val obj = JSONObject(raw)
        return obj.keys().asSequence().associateWith { key ->
            val a = obj.optJSONArray(key) ?: JSONArray()
            List(a.length()) { a.optLong(it) }
        }
    }

    fun savePlaylists(value: Map<String, List<Long>>) {
        val obj = JSONObject()
        value.forEach { (name, ids) ->
            obj.put(name, JSONArray(ids))
        }
        prefs.edit().putString("playlists", obj.toString()).apply()
    }

    fun lastQueue(): List<Long> =
        prefs.getString("queue", "")?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun saveQueue(ids: List<Long>) =
        prefs.edit().putString("queue", ids.joinToString(",")).apply()
}
