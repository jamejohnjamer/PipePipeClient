package org.schabi.newpipe.error

/** Pure-Kotlin size limits for error data that may be parceled (Bundles / Intents). */
object ErrorTraceLimits {
    // 4000, not 6000: the test budget is 1,000,000 bytes and 5 traces x 6000 chars x 2
    // (UTF-16) = 1,200,000 bytes, which fails worstCaseSavedErrorBudget. 5 x 4000 x 2 = 800,000.
    const val MAX_TRACE_CHARS = 4000
    const val MAX_TRACES = 5

    @JvmStatic
    fun capTrace(trace: String): String =
        if (trace.length <= MAX_TRACE_CHARS) trace
        else trace.substring(0, MAX_TRACE_CHARS) + "\n... [truncated]"
}
