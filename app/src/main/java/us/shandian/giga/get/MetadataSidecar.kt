package us.shandian.giga.get

import android.util.Log

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

        val storage = m.storage
        TagCheck.checkAsync(m)
        Thread({
            try {
                if (!storage.writeSiblingFile(finalName, bytes)) {
                    Log.w(TAG, "could not write sidecar (download is unaffected)")
                    report(context, "storage refused writing $finalName")
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
}
