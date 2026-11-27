package us.shandian.giga.get

/**
 * Metadata arguments for the ffmpeg remux steps that ALREADY exist (HLS and SABR downloads).
 * They only add "-metadata key=value" options to the same single pass: no extra processing.
 * Values that are missing are skipped. Empty list when there is nothing to write.
 */
internal object FfmpegTagArgs {
    private fun clean(v: String?): String? =
        v?.replace(Regex("[\\u0000-\\u001F]"), " ")?.trim()?.take(300)?.takeIf { it.isNotEmpty() }

    fun build(m: DownloadMission): List<String> {
        val out = ArrayList<String>()
        clean(m.tagArtist)?.let { out += listOf("-metadata", "artist=$it") }
        clean(m.tagTitle)?.let { out += listOf("-metadata", "title=$it") }
        clean(m.tagDate)?.let {
            out += listOf("-metadata", "date=${it.take(10)}")
            if (it.length > 10) out += listOf("-metadata", "creation_time=$it")
        }
        return out
    }
}
