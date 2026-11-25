package us.shandian.giga.get

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicFileWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesCompleteContentAndLeavesNoTempFile() {
        val target = File(tmp.root, "a.mp4.metadata.json")
        assertTrue(AtomicFileWriter.write(target, "{\"x\":1}".toByteArray()))
        assertArrayEquals("{\"x\":1}".toByteArray(), target.readBytes())
        assertFalse(File(tmp.root, "a.mp4.metadata.json.tmp").exists())
    }

    @Test fun replacesAnOlderSidecar() {
        val target = File(tmp.root, "a.mp4.metadata.json").apply { writeText("old old old old") }
        assertTrue(AtomicFileWriter.write(target, "new".toByteArray()))
        assertEquals("new", target.readText())
    }

    @Test fun failureReturnsFalseAndLeavesNothingBehind() {
        val target = File(File(tmp.root, "missing-folder"), "a.metadata.json")
        assertFalse(AtomicFileWriter.write(target, "data".toByteArray()))
        assertFalse(target.exists())
        assertEquals(0, tmp.root.listFiles()!!.size)
    }

    @Test fun neverTouchesTheMediaFile() {
        val media = File(tmp.root, "a.mp4").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
        val before = media.readBytes()
        val stamp = media.lastModified()
        AtomicFileWriter.write(File(tmp.root, "a.mp4.metadata.json"), "{}".toByteArray())
        assertArrayEquals(before, media.readBytes())
        assertEquals(stamp, media.lastModified())
    }
}
