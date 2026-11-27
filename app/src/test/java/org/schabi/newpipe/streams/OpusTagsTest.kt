package org.schabi.newpipe.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusTagsTest {
    @Test fun nothingToWriteGivesNull() {
        assertNull(OggFromWebMWriter.buildOpusTags(null, " ", ""))
    }

    @Test fun packetHasOpusTagsMagicAndTheComments() {
        val p = OggFromWebMWriter.buildOpusTags("Chan", "Title", "2025-08-17")!!
        assertEquals("OpusTags", String(p, 0, 8, Charsets.US_ASCII))
        val bb = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0, bb.getInt(8))   // vendor length
        assertEquals(3, bb.getInt(12))  // comment count
        val text = String(p, Charsets.UTF_8)
        assertTrue(text.contains("ARTIST=Chan") && text.contains("TITLE=Title") && text.contains("DATE=2025-08-17"))
    }

    @Test fun missingUploaderSkipsArtistButKeepsDate() {
        val text = String(OggFromWebMWriter.buildOpusTags(null, null, "2025-08-17")!!, Charsets.UTF_8)
        assertTrue(text.contains("DATE=2025-08-17"))
        assertEquals(-1, text.indexOf("ARTIST="))
    }
}
