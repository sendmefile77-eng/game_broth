package com.sendmefile77.gamebroth.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.sendmefile77.gamebroth.ai.PngPayload
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class GalleryFileStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "staff_gallery").apply {
        check(isDirectory || mkdirs()) { "Не удалось создать каталог галереи: $absolutePath" }
    }

    /** Atomically writes a validated PNG and verifies that Android can read its dimensions. */
    fun savePng(staffId: String, frameId: String, bytes: ByteArray): String {
        require(staffId.isNotBlank())
        require(frameId.isNotBlank())
        require(PngPayload.isPng(bytes)) { "Сохранение отменено: данные не являются полным PNG" }

        val relativePath = relativePath(staffId, frameId)
        val file = resolve(relativePath)
        val dir = file.parentFile ?: error("Не удалось определить каталог изображения")
        check(dir.isDirectory || dir.mkdirs()) { "Не удалось создать каталог изображения: ${dir.absolutePath}" }
        val temp = File(dir, ".${file.name}.${Thread.currentThread().id}.tmp")

        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(temp.isFile && temp.length() == bytes.size.toLong()) {
                "Временный PNG записан не полностью: ${temp.length()} из ${bytes.size} байт"
            }
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }

        check(file.isFile && file.length() == bytes.size.toLong()) {
            "PNG отсутствует после записи или имеет неверный размер"
        }
        check(isReadablePng(relativePath)) { "Сохранённый PNG не читается Android: ${file.absolutePath}" }
        Log.i(TAG, "FILE saved: ${file.absolutePath} (${file.length()} bytes)")
        Log.i(TAG, "FILE exists: ${file.isFile}")
        return relativePath
    }

    fun relativePath(staffId: String, frameId: String): String {
        require(staffId.isNotBlank())
        require(frameId.isNotBlank())
        return File(safe(staffId), "${safe(frameId)}.png").path
    }

    fun read(relativePath: String): ByteArray? = resolve(relativePath).takeIf(File::isFile)?.readBytes()
    fun absolutePath(relativePath: String): String = resolve(relativePath).absolutePath
    fun exists(relativePath: String): Boolean = resolve(relativePath).isFile
    fun lastModified(relativePath: String): Long = resolve(relativePath).takeIf(File::isFile)?.lastModified() ?: 0L

    fun dimensions(relativePath: String): Pair<Int, Int>? {
        val file = runCatching { resolve(relativePath) }.getOrNull()?.takeIf(File::isFile) ?: return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }

    fun isReadablePng(relativePath: String): Boolean {
        val file = runCatching { resolve(relativePath) }.getOrNull() ?: return false
        if (!file.isFile || file.length() <= 20L) return false
        val signatureOkay = runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(8)
                input.read(head) == head.size &&
                    head.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
            }
        }.getOrDefault(false)
        if (!signatureOkay) return false
        return dimensions(relativePath) != null
    }

    private fun resolve(relativePath: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relativePath).canonicalFile
        require(candidate.path.startsWith(canonicalRoot.path + File.separator)) { "Invalid gallery path" }
        return candidate
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val TAG = "PortraitPipeline"
    }
}
