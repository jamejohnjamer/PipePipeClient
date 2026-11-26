package us.shandian.giga.get

import android.util.Log
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import java.io.File
import java.io.IOException

/**
 * Writes "<media file name>.metadata.json" next to a finished download.
 *
 * - Never opens, reads, copies or modifies the media file.
 * - Runs on its own short-lived thread started AFTER the mission is marked finished, so it can not
 *   delay or fail the download. Any error is only logged.
 * - Uses only information the app already had when the download was started (no network).
 * - Written atomically: temp file first, then rename (a partial JSON is never left under the final name).
 * - The name comes from the mission's final storage name, i.e. after any collision handling.
 */
object MetadataSidecar {
    private const val TAG = "MetadataSidecar"
    const val SUFFIX = SidecarNames.SUFFIX

    @JvmStatic
    fun hasData(m: DownloadMission): Boolean =
        (m.kind == 'a' || m.kind == 'v') &&
            (!m.tagArtist.isNullOrBlank() || !m.tagDate.isNullOrBlank() || !m.tagTitle.isNullOrBlank() ||
                !m.tagVideoId.isNullOrBlank())

    @JvmStatic
    fun writeAsync(m: DownloadMission) {
        if (!hasData(m)) return
        val mediaName = m.storage.name ?: return
        val parent = try { m.storage.parentUri } catch (e: Exception) { null }
        val ioFile: File? = m.storage.ioFile
        val tag = m.storage.tag
        val context = m.context
        val date = m.tagDate.orEmpty()
        val fields = LinkedHashMap<String, String>()
        fields["title"] = m.tagTitle.orEmpty()
        fields["uploader"] = m.tagArtist.orEmpty()
        fields["channel_id"] = SidecarJson.channelIdFromUrl(m.tagChannelUrl).orEmpty()
        fields["channel_url"] = m.tagChannelUrl.orEmpty()
        fields["upload_date"] = date.take(10)
        fields["upload_datetime_utc"] = if (date.length > 10) date else ""
        fields["video_id"] = m.tagVideoId.orEmpty()
        fields["url"] = m.source.orEmpty()
        // aliases used by other tools / the player's own JSON convention
        fields["channel_name"] = m.tagArtist.orEmpty()
        fields["original_url"] = m.source.orEmpty()
        fields["original_title"] = m.tagTitle.orEmpty()
        fields["platform"] = if ((m.source ?: "").contains("youtu")) "YouTube" else ""
        fields["downloaded_by"] = "Dinny"
        fields["format"] = mediaName.substringAfterLast('.', "").uppercase()
        fields["media_type"] = if (m.kind == 'a') "audio" else "video"
        fields["media_filename"] = mediaName
        fields["download_status"] = "completed"
        val bytes = SidecarJson.build(fields).toByteArray(Charsets.UTF_8)
        val finalName = SidecarNames.forMedia(mediaName)

        Thread({
            try {
                if (ioFile != null) {
                    val dir = ioFile.parentFile ?: return@Thread
                    if (!AtomicFileWriter.write(File(dir, finalName), bytes)) {
                        Log.w(TAG, "could not write sidecar (download is unaffected)")
                        report(context, "write to folder failed for $finalName")
                    }
                } else if (parent != null && context != null) {
                    writeViaStorage(context, parent, tag, mediaName, finalName, bytes)
                } else {
                    report(context, "no folder information for $finalName (parent=$parent)")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "could not write sidecar (download is unaffected)", e)
                report(context, "${e.javaClass.simpleName}: ${e.message}")
            }
        }, "metadata-sidecar").apply { isDaemon = true }.start()
    }

    /** Makes sidecar problems visible in the drawer > Recent errors list (download itself is unaffected). */
    private fun report(context: android.content.Context?, reason: String) {
        if (context == null) return
        org.schabi.newpipe.error.RecentErrors.recordNote(context, "Sidecar", reason, "")
    }

    private fun writeViaStorage(
        context: android.content.Context,
        parent: android.net.Uri,
        tag: String?,
        mediaName: String,
        finalName: String,
        bytes: ByteArray
    ) {
        val dir = StoredDirectoryHelper(context, parent, tag)
        val tmp = dir.createFile(SidecarNames.tempFor(mediaName), "application/octet-stream")
        if (tmp == null) {
            report(context, "could not create temp file in the download folder")
            return
        }
        var done = false
        try {
            tmp.stream.use { out ->
                out.setLength(0)
                out.write(bytes)
            }
            tmp.deleteSibling(finalName) // a sidecar from an earlier download of the same name
            done = tmp.renameFileTo(finalName)
        } catch (e: IOException) {
            done = false
        }
        if (!done) {
            // rename not supported by this storage provider: remove the temp file, write directly (tiny file)
            try { tmp.delete() } catch (ignored: Exception) { }
            dir.createFile(finalName, "application/json")?.stream?.use { out ->
                out.setLength(0)
                out.write(bytes)
            }
        }
    }
}
