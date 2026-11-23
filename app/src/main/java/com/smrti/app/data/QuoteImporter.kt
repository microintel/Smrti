package com.smrti.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads quotes from JSON. Accepts:
 *  - ["quote one", "quote two"]
 *  - [{"text": "...", "author": "...", "category": "..."}]
 *  - {"quotes": [ ... ]}
 * Field names are flexible: text/quote/content/message/dialogue, author/by/name/speaker/character/source,
 * category/type/tag.
 */
object QuoteImporter {

    fun parse(json: String): List<Quote> {
        val trimmed = json.trim().removePrefix("﻿")
        val array: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val o = JSONObject(trimmed)
            listOf("quotes", "data", "items", "list")
                .firstNotNullOfOrNull { o.optJSONArray(it) } ?: JSONArray()
        }

        val out = ArrayList<Quote>()
        for (i in 0 until array.length()) {
            when (val item = array.get(i)) {
                is String -> if (item.isNotBlank()) out += Quote(text = item.trim())
                is JSONObject -> {
                    val fields = HashMap<String, String>()
                    val keys = item.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (!item.isNull(k)) fields[k.lowercase()] = item.optString(k).trim()
                    }
                    fun pick(vararg names: String) =
                        names.firstNotNullOfOrNull { fields[it]?.takeIf { v -> v.isNotEmpty() } }

                    val text = pick("text", "quote", "content", "message", "dialogue", "dialog", "body")
                        ?: continue
                    out += Quote(
                        text = text,
                        author = pick("author", "by", "name", "speaker", "character", "source").orEmpty(),
                        category = pick("category", "type", "tag", "kind")
                            ?.replaceFirstChar { it.uppercase() } ?: "Quote"
                    )
                }
            }
        }
        return out
    }

    /** The whole collection as JSON the importer can read back (so it doubles as a backup). */
    suspend fun exportJson(context: Context): String {
        val array = JSONArray()
        for (q in AppDatabase.get(context).quotes().all()) {
            array.put(JSONObject().put("text", q.text).put("author", q.author).put("category", q.category))
        }
        return array.toString(2)
    }

    /** Inserts parsed quotes, skipping duplicates. Returns how many were new. */
    suspend fun importJson(context: Context, json: String): Int {
        val quotes = parse(json)
        val ids = AppDatabase.get(context).quotes().insertAll(quotes)
        return ids.count { it != -1L }
    }

    // The quotes that shipped before; removed once from phones that already have them,
    // so the app starts with only the current defaults. Quotes you typed in yourself are never touched.
    private val oldDefaults = listOf(
        "You have power over your mind, not outside events. Realize this, and you will find strength.",
        "We suffer more often in imagination than in reality.",
        "The journey of a thousand miles begins with a single step.",
        "It does not matter how slowly you go as long as you do not stop.",
        "The best way to predict the future is to create it.",
        "Small steps every day. That is how mountains get climbed.",
        "Do what you can, with what you have, where you are.",
        "Whether you think you can, or you think you can't, you're right.",
        "To be, or not to be, that is the question.",
        "All the world's a stage, and all the men and women merely players.",
        "Well begun is half done.",
        "Remember why you started."
    )

    /** First launch: load the bundled assets/quotes.json once. */
    suspend fun seedIfNeeded(context: Context) {
        val dao = AppDatabase.get(context).quotes()
        if (!Prefs.isDefaultsV2(context)) {
            // update from an earlier version: drop the old built-in quotes, then add the new defaults
            if (Prefs.isSeeded(context)) dao.deleteDefaults(oldDefaults)
            loadBundled(context)
            Prefs.setSeeded(context)
            Prefs.setDefaultsV2(context)
            Prefs.setDefaultsV3(context)
            return
        }
        if (!Prefs.isDefaultsV3(context)) {
            // 1.2 ships a bigger starter set: add the ones this phone doesn't have yet (duplicates are skipped)
            loadBundled(context)
            Prefs.setSeeded(context)
            Prefs.setDefaultsV3(context)
            return
        }
        if (Prefs.isSeeded(context)) return
        loadBundled(context)
        Prefs.setSeeded(context)
    }

    private suspend fun loadBundled(context: Context) {
        try {
            val json = context.assets.open("quotes.json").bufferedReader().use { it.readText() }
            importJson(context, json)
        } catch (_: Exception) {
            // no bundled file or bad JSON: start empty
        }
    }
}
