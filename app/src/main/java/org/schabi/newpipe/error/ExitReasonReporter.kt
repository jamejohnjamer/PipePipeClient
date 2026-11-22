package org.schabi.newpipe.error

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * ANRs and background kills are not exceptions, so no crash handler sees them. Android 11+ keeps
 * a record of why each process died (ApplicationExitInfo). On start we copy new records into
 * [RecentErrors] (bounded) so the main-thread stack of an ANR is available on the phone itself.
 * Runs on a background thread; never throws.
 */
object ExitReasonReporter {
    private const val PREFS = "exit_reason_reporter"
    private const val KEY_LAST = "last_timestamp"

    @JvmStatic
    fun captureAsync(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = context.applicationContext
        Thread({
            try {
                capture(app)
            } catch (ignored: Throwable) {
            }
        }, "exit-reason-capture").start()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun capture(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST, 0L)
        var newest = last
        // oldest first, so the newest ends up on top of the list
        for (info in am.getHistoricalProcessExitReasons(null, 0, 6).sortedBy { it.timestamp }) {
            if (info.timestamp <= last) continue
            newest = maxOf(newest, info.timestamp)
            val reason = info.reason
            if (reason == ApplicationExitInfo.REASON_EXIT_SELF ||
                reason == ApplicationExitInfo.REASON_USER_REQUESTED ||
                reason == ApplicationExitInfo.REASON_USER_STOPPED
            ) {
                continue
            }
            var trace = ""
            if (reason == ApplicationExitInfo.REASON_ANR || reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                trace = try {
                    info.traceInputStream?.bufferedReader()?.use { it.readText().take(60_000) }.orEmpty()
                } catch (e: Exception) {
                    ""
                }
                val mainAt = trace.indexOf("\"main\"")
                if (mainAt > 0) trace = trace.substring(mainAt) // start at the main-thread stack
            }
            val summary = "${reasonName(reason)}: ${info.description.orEmpty()} " +
                "(importance=${info.importance}, pss=${info.pss / 1024} MB)"
            RecentErrors.recordNote(context, "Process exit", summary, trace)
        }
        prefs.edit().putLong(KEY_LAST, newest).apply()
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
        ApplicationExitInfo.REASON_CRASH -> "Java crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed: low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Killed: excessive resource use"
        ApplicationExitInfo.REASON_SIGNALED -> "Killed by signal (system/OEM task killer?)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Failed to start"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "Permission change"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Dependency died"
        ApplicationExitInfo.REASON_OTHER -> "Other (system)"
        else -> "Exit reason $reason"
    }
}
