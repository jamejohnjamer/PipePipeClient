package org.schabi.newpipe.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusTagsTest {
    @Test fun nothingToWriteGivesNull() {
        assertNull(OggFromWebMWriter.buildOpusTags(null, " ", null))
    }

    @Test fun packetIsWellFormed() {
        val p = OggFromWebMWriter.buildOpusTags("Chan", "Title", "2025-08-17")!!
        val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("OpusTags", String(p, 0, 8))
        assertEquals(0, b.getInt(8))   // vendor length
        assertEquals(3, b.getInt(12))  // comment count
        val text = String(p, Charsets.UTF_8)
        assertTrue(text.contains("ARTIST=Chan") && text.contains("TITLE=Title") && text.contains("DATE=2025-08-17"))
    }

    @Test fun missingUploaderIsSkippedButDateKept() {
        val p = OggFromWebMWriter.buildOpusTags(null, null, "2025-08-17")!!
        assertEquals(1, ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN).getInt(12))
        assertTrue(!String(p).contains("ARTIST"))
    }
}
