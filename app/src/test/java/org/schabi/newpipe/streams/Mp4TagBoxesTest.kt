package org.schabi.newpipe.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class Mp4TagBoxesTest {
    private fun fourcc(b: ByteArray, off: Int) = String(b, off, 4, Charsets.ISO_8859_1)

    @Test fun nothingToWriteGivesEmptyArray() {
        assertEquals(0, Mp4FromDashWriter.buildTagBoxes(null, "", "  ").size)
    }

    @Test fun boxSizesAreConsistentAndContainTheText() {
        val box = Mp4FromDashWriter.buildTagBoxes("My Channel", "My Title", "2026-10-20")
        val bb = ByteBuffer.wrap(box)
        assertEquals(box.size, bb.getInt(0))               // udta size = whole array
        assertEquals("udta", fourcc(box, 4))
        assertEquals(box.size - 8, bb.getInt(8))           // meta fills udta
        assertEquals("meta", fourcc(box, 12))
        assertEquals("hdlr", fourcc(box, 24))              // after 4 bytes version/flags
        assertEquals(33, bb.getInt(20))
        val ilstAt = 20 + 33
        assertEquals("ilst", fourcc(box, ilstAt + 4))
        assertEquals(box.size - ilstAt, bb.getInt(ilstAt)) // ilst runs to the end
        val text = String(box, Charsets.UTF_8)
        assertTrue(text.contains("My Channel") && text.contains("My Title") && text.contains("2026-10-20"))
    }

    @Test fun missingUploaderStillWritesDate() {
        val box = Mp4FromDashWriter.buildTagBoxes(null, null, "2026-10-20")
        assertTrue(box.isNotEmpty())
        assertTrue(String(box, Charsets.UTF_8).contains("2026-10-20"))
        assertEquals(-1, String(box, Charsets.ISO_8859_1).indexOf("ART"))
    }
}
