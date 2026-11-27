package us.shandian.giga.get

import android.media.MediaMetadataRetriever
import android.util.Log
import org.schabi.newpipe.error.RecentErrors

/**
 * Diagnostic: after a download finished, asks ANDROID ITSELF (MediaMetadataRetriever, read-only, header
 * information only) which artist/title/date/year it can read from the file, and writes one line
 * to drawer > Recent errors ("Tag check"). This is the ground truth for what a normal Android player
 * can see. It never modifies or copies the file and runs on its own thread after the mission is finished.
 */
object TagCheck {
    private const val TAG = "TagCheck"

    @JvmStatic
    fun checkAsync(m: DownloadMission) {
        val context = m.context ?: return
        val storage = m.storage
        val name = storage.name ?: return
        Thread({
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, storage.uri)
                fun k(key: Int) = mmr.extractMetadata(key) ?: "-"
                val line = "$name -> Android reads: artist=${k(MediaMetadataRetriever.METADATA_KEY_ARTIST)}" +
                    " | title=${k(MediaMetadataRetriever.METADATA_KEY_TITLE)}" +
                    " | date=${k(MediaMetadataRetriever.METADATA_KEY_DATE)}" +
                    " | year=${k(MediaMetadataRetriever.METADATA_KEY_YEAR)}"
                RecentErrors.recordNote(context, "Tag check", line, "")
            } catch (e: Throwable) {
                Log.w(TAG, "tag check failed", e)
                RecentErrors.recordNote(context, "Tag check", "$name -> could not read: ${e.javaClass.simpleName}", "")
            } finally {
                try { mmr.release() } catch (ignored: Throwable) { }
            }
        }, "tag-check").apply { isDaemon = true }.start()
    }
}
