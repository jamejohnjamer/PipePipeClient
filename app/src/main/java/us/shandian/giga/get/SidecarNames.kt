package us.shandian.giga.get

/** Pure naming rules for the metadata sidecar (unit tested). */
object SidecarNames {
    const val SUFFIX = ".metadata.json"
    private const val TEMP_EXTRA = ".tmp"

    @JvmStatic fun forMedia(mediaName: String): String = mediaName + SUFFIX

    @JvmStatic fun tempFor(mediaName: String): String = mediaName + SUFFIX + TEMP_EXTRA

    /** True only for the exact sidecar of this media file, never for any other file. */
    @JvmStatic fun isSidecarOf(mediaName: String, candidate: String): Boolean =
        candidate == forMedia(mediaName)
}
