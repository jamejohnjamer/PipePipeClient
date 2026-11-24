package org.schabi.newpipe.error

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper

/**
 * Tiny foreground-only sampler: every 20 s it stores ONE short line (heap, native memory, thread count,
 * biggest thread-name groups) in SharedPreferences. After an ANR / low-memory kill, the last line is
 * attached to the "Process exit" entry, so we can see whether threads or memory were exploding.
 * Stops when the app leaves the foreground; overwrites a single value (bounded).
 */
object DiagSampler {
    private const val PREFS = "diag_sampler"
    private const val KEY = "last_snapshot"
    private const val INTERVAL_MS = 20_000L
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            val ctx = appContext ?: return
            // sampling itself runs off the UI thread
            Thread({ store(ctx) }, "diag-sampler").apply { isDaemon = true }.start()
            if (running) handler.postDelayed(this, INTERVAL_MS)
        }
    }

    @JvmStatic
    fun start(context: Context) {
        appContext = context.applicationContext
        if (running) return
        running = true
        handler.postDelayed(tick, 5_000L)
    }

    @JvmStatic
    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    @JvmStatic
    fun snapshot(): String = try {
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576
        val maxMb = rt.maxMemory() / 1048576
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1048576
        val threads = Thread.getAllStackTraces().keys
        val groups = threads.groupingBy { it.name.replace(Regex("[0-9]+"), "#") }.eachCount()
            .entries.sortedByDescending { it.value }.take(6).joinToString(", ") { "${it.key}x${it.value}" }
        "javaHeap=${usedMb}/${maxMb}MB native=${nativeMb}MB threads=${threads.size} top=[$groups]"
    } catch (e: Throwable) {
        "snapshot failed"
    }

    private fun store(ctx: Context) {
        try {
            val line = "${System.currentTimeMillis()} ${snapshot()}"
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, line).apply()
        } catch (ignored: Throwable) {
        }
    }

    @JvmStatic
    fun lastStored(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
}
