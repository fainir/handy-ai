package com.claudeagent.phone

import android.content.Context
import org.json.JSONObject

/**
 * User-defined name aliases. Maps a short label the user actually
 * says ("Mom", "the babysitter", "my usual order") to a concrete
 * entity the agent should resolve to.
 *
 * The agent reads these from the system prompt at task start; the
 * user manages them via Settings → Named aliases.
 */
object NameAliases {
    private const val PREFS = "name_aliases"
    private const val KEY = "aliases_json"
    private const val MAX_ENTRIES = 30
    private const val MAX_KEY_LEN = 60
    private const val MAX_VALUE_LEN = 240

    fun all(context: Context): Map<String, String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = mutableMapOf<String, String>()
            obj.keys().forEach { k ->
                val v = obj.optString(k, "").trim()
                if (v.isNotEmpty()) out[k.lowercase()] = v
            }
            out
        } catch (t: Throwable) {
            emptyMap()
        }
    }

    fun set(context: Context, alias: String, entity: String) {
        val a = alias.trim().take(MAX_KEY_LEN).lowercase()
        val e = entity.trim().take(MAX_VALUE_LEN)
        if (a.isEmpty() || e.isEmpty()) return
        val current = all(context).toMutableMap()
        current[a] = e
        if (current.size > MAX_ENTRIES) {
            val trimmed = current.entries.toList()
                .takeLast(MAX_ENTRIES)
                .associate { it.key to it.value }
            persist(context, trimmed)
        } else {
            persist(context, current)
        }
    }

    fun remove(context: Context, alias: String) {
        val a = alias.trim().take(MAX_KEY_LEN).lowercase()
        val current = all(context).toMutableMap()
        if (current.remove(a) != null) persist(context, current)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    /**
     * Render the alias table as a bullet list for the agent system
     * prompt. Returns "" if no aliases are set.
     */
    fun format(context: Context): String {
        val map = all(context)
        if (map.isEmpty()) return ""
        return map.entries.joinToString("\n") { (alias, entity) ->
            "- \"$alias\" → $entity"
        }
    }

    private fun persist(context: Context, map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, obj.toString()).apply()
    }
}
