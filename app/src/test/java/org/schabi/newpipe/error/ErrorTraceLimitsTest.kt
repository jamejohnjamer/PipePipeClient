package org.schabi.newpipe.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTraceLimitsTest {
    @Test fun shortTraceUnchanged() = assertEquals("abc", ErrorTraceLimits.capTrace("abc"))

    @Test fun longTraceIsCapped() {
        val capped = ErrorTraceLimits.capTrace("x".repeat(100_000))
        assertTrue(capped.length < ErrorTraceLimits.MAX_TRACE_CHARS + 50)
    }

    /** Guardrail: 20 fragments x worst-case error must stay far below the ~1 MB Binder limit. */
    @Test fun worstCaseSavedErrorBudget() {
        val perError = ErrorTraceLimits.MAX_TRACES * (ErrorTraceLimits.MAX_TRACE_CHARS * 2) // UTF-16
        assertTrue(20 * perError < 1_000_000)
    }
}
