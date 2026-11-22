package org.schabi.newpipe.error

import android.content.Context
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Keeps the last [MAX_ENTRIES] errors in a tiny, bounded SharedPreferences store (never in
 * saved-instance-state). Each entry is size-capped, so this can not grow without limit.
 */
object RecentErrors {
    private const val PREFS = "recent_errors_store"
    private const val KEY = "items"
    const val MAX_ENTRIES = 5
    private const val MAX_SUMMARY = 300
    private const val MAX_DETAIL = 1500
    private const val MAX_REQUEST = 200

    data class Entry(val time: Long, val action: String, val request: String, val summary: String, val detail: String)

    @JvmStatic
    @Synchronized
    fun record(context: Context, errorInfo: ErrorInfo) {
        try {
            val firstTrace = errorInfo.stackTraces.firstOrNull().orEmpty()
            val entry = JSONObject()
                .put("t", System.currentTimeMillis())
                .put("a", errorInfo.userAction.toString().take(80))
                .put("r", errorInfo.request.take(MAX_REQUEST))
                .put("s", firstTrace.lineSequence().firstOrNull().orEmpty().take(MAX_SUMMARY))
                .put("d", firstTrace.take(MAX_DETAIL))
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = JSONArray(prefs.getString(KEY, "[]"))
            val updated = JSONArray().put(entry)
            var i = 0
            while (i < old.length() && updated.length() < MAX_ENTRIES) {
                updated.put(old.get(i))
                i++
            }
            prefs.edit().putString(KEY, updated.toString()).apply()
        } catch (ignored: Exception) {
            // recording an error must never cause another error
        }
    }

    @JvmStatic
    @Synchronized
    fun all(context: Context): List<Entry> = try {
        val arr = JSONArray(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]")
        )
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.optLong("t"), o.optString("a"), o.optString("r"), o.optString("s"), o.optString("d"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    @JvmStatic
    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    @JvmStatic
    fun showDialog(context: Context) {
        val entries = all(context)
        val text = if (entries.isEmpty()) {
            "No errors recorded."
        } else {
            entries.joinToString("\n\n") {
                val whenText = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(it.time))
                "$whenText  [${it.action}]\n${it.request}\n${it.summary}"
            }
        }
        AlertDialog.Builder(context)
            .setTitle("Recent errors (last $MAX_ENTRIES)")
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Clear") { _, _ -> clear(context) }
            .show()
    }
}
