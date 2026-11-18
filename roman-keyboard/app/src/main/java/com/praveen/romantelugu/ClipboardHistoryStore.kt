package com.praveen.romantelugu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardEntry(
    val id: Long,
    val text: String,
    val pinned: Boolean
)

class ClipboardHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "clipboard_history",
        Context.MODE_PRIVATE
    )

    private val key = "items"
    private val maxItems = 30

    @Synchronized
    fun getAll(): List<ClipboardEntry> {
        val result = mutableListOf<ClipboardEntry>()

        try {
            val array = JSONArray(prefs.getString(key, "[]"))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val text = item.optString("text", "")
                if (text.isNotBlank()) {
                    result.add(
                        ClipboardEntry(
                            item.optLong("id"),
                            text,
                            item.optBoolean("pinned", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return result.sortedWith(
            compareByDescending<ClipboardEntry> { it.pinned }
                .thenByDescending { it.id }
        )
    }

    @Synchronized
    fun add(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val items = getAll().toMutableList()

        if (items.any { it.text == cleanText }) return

        items.add(
            0,
            ClipboardEntry(
                System.currentTimeMillis(),
                cleanText,
                false
            )
        )

        save(items)
    }

    @Synchronized
    fun togglePin(id: Long) {
        val items = getAll().map {
            if (it.id == id) it.copy(pinned = !it.pinned) else it
        }
        save(items)
    }

    @Synchronized
    fun delete(id: Long) {
        save(getAll().filterNot { it.id == id })
    }

    @Synchronized
    fun clearUnpinned() {
        save(getAll().filter { it.pinned })
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().remove(key).apply()
    }

    @Synchronized
    private fun save(items: List<ClipboardEntry>) {
        val pinned = items.filter { it.pinned }
        val recent = items.filterNot { it.pinned }.take(maxItems)

        val array = JSONArray()
        (pinned + recent).forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("text", it.text)
                    put("pinned", it.pinned)
                }
            )
        }

        prefs.edit().putString(key, array.toString()).apply()
    }
}
