package us.shandian.giga.get

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Writes a small file via "<name>.tmp" + rename, so readers never see partial content. */
object AtomicFileWriter {
    /** @return true if [target] now holds exactly [data]; on any failure the temp file is removed. */
    @JvmStatic
    fun write(target: File, data: ByteArray): Boolean {
        val dir = target.parentFile ?: return false
        val tmp = File(dir, target.name + ".tmp")
        return try {
            FileOutputStream(tmp).use {
                it.write(data)
                it.fd.sync()
            }
            if (tmp.renameTo(target)) {
                true
            } else {
                tmp.delete()
                false
            }
        } catch (e: IOException) {
            tmp.delete()
            false
        }
    }
}
