package us.shandian.giga.get

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarNamesTest {
    @Test fun nameFollowsTheFinalMediaName() {
        // e.g. after collision handling the media is "Video (1).mp4": sidecar must follow it
        assertEquals("Video (1).mp4.metadata.json", SidecarNames.forMedia("Video (1).mp4"))
        assertEquals("a.m4a.metadata.json.tmp", SidecarNames.tempFor("a.m4a"))
    }

    @Test fun onlyTheExactSidecarMatches() {
        assertTrue(SidecarNames.isSidecarOf("a.mp4", "a.mp4.metadata.json"))
        assertFalse(SidecarNames.isSidecarOf("a.mp4", "b.mp4.metadata.json"))
        assertFalse(SidecarNames.isSidecarOf("a.mp4", "a.mp4"))
        assertFalse(SidecarNames.isSidecarOf("a.mp4", "a.mp4.metadata.json.tmp"))
        assertFalse(SidecarNames.isSidecarOf("a.mp4", "notes.json"))
    }
}
