package org.schabi.newpipe.error

/** Pure-Kotlin size limits for error data that may be parceled (Bundles / Intents). */
object ErrorTraceLimits {
    const val MAX_TRACE_CHARS = 6000
    const val MAX_TRACES = 5

    @JvmStatic
    fun capTrace(trace: String): String =
        if (trace.length <= MAX_TRACE_CHARS) trace
        else trace.substring(0, MAX_TRACE_CHARS) + "\n... [truncated]"
}
