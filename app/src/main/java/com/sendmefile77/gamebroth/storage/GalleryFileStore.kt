package com.sendmefile77.gamebroth.storage

import android.content.Context
import java.io.File

class GalleryFileStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "staff_gallery").apply { mkdirs() }

    fun savePng(staffId: String, frameId: String, bytes: ByteArray): String {
        require(staffId.isNotBlank())
        require(frameId.isNotBlank())
        require(bytes.isNotEmpty())
        val dir = File(root, safe(staffId)).apply { mkdirs() }
        val file = File(dir, "${safe(frameId)}.png")
        file.writeBytes(bytes)
        return file.relativeTo(root).path
    }

    fun read(relativePath: String): ByteArray? = resolve(relativePath).takeIf(File::isFile)?.readBytes()
    fun absolutePath(relativePath: String): String = resolve(relativePath).absolutePath
    fun exists(relativePath: String): Boolean = resolve(relativePath).isFile

    private fun resolve(relativePath: String): File {
        val candidate = File(root, relativePath).canonicalFile
        require(candidate.path.startsWith(root.canonicalFile.path + File.separator)) { "Invalid gallery path" }
        return candidate
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
