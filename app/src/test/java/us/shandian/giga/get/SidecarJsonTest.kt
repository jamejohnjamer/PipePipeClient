package us.shandian.giga.get

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarJsonTest {
    @Test fun missingFieldsAreOmitted() {
        val json = SidecarJson.build(linkedMapOf("title" to "T", "uploader" to "", "upload_date" to "2026-10-20"))
        assertTrue(json.contains("\"title\": \"T\""))
        assertTrue(json.contains("\"upload_date\": \"2026-10-20\""))
        assertFalse(json.contains("uploader"))
    }

    @Test fun specialCharactersAreEscaped() {
        assertEquals("a\\\"b\\\\c\\nd", SidecarJson.escape("a\"b\\c\nd"))
    }

    @Test fun channelIdParsedOnlyFromChannelUrls() {
        assertEquals(
            "UCabcdefghijklmnopqrstuv",
            SidecarJson.channelIdFromUrl("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv")
        )
        assertEquals(null, SidecarJson.channelIdFromUrl("https://www.youtube.com/@handle"))
        assertEquals(null, SidecarJson.channelIdFromUrl(null))
    }

    @Test fun emptyInputGivesEmptyObject() {
        assertEquals("{\n\n}\n", SidecarJson.build(emptyMap()))
    }
}
