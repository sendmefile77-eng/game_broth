package com.sendmefile77.gamebroth.storage

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class GameBackupStore(context: Context) {
    private val appContext = context.applicationContext
    private val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
    private val galleryRoot = File(appContext.filesDir, GALLERY_DIR)

    /** Repository/SQLite connections must be closed before this method is called. */
    fun exportTo(output: OutputStream) {
        require(databaseFile.isFile) { "Save database does not exist" }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            writeText(zip, MANIFEST_ENTRY, "format=$FORMAT_VERSION\ndatabase=$DATABASE_ENTRY\ngallery=$GALLERY_PREFIX\n")
            writeFile(zip, DATABASE_ENTRY, databaseFile)
            if (galleryRoot.isDirectory) {
                galleryRoot.walkTopDown()
                    .filter(File::isFile)
                    .forEach { file ->
                        val relative = file.relativeTo(galleryRoot).invariantSeparatorsPath
                        writeFile(zip, "$GALLERY_PREFIX$relative", file)
                    }
            }
        }
    }

    /** Repository/SQLite connections must be closed before this method is called. */
    fun importFrom(input: InputStream) {
        val stage = File(appContext.cacheDir, "game_broth_import_stage").apply {
            deleteRecursively()
            mkdirs()
        }
        val stagedDb = File(stage, "database/$DATABASE_NAME")
        val stagedGallery = File(stage, GALLERY_DIR)
        try {
            extractValidated(input, stage)
            require(stagedDb.isFile) { "Backup has no save database" }
            validateSqlite(stagedDb)

            val rollback = File(appContext.cacheDir, "game_broth_import_rollback").apply {
                deleteRecursively()
                mkdirs()
            }
            val rollbackDb = File(rollback, DATABASE_NAME)
            val rollbackGallery = File(rollback, GALLERY_DIR)
            if (databaseFile.isFile) databaseFile.copyTo(rollbackDb, overwrite = true)
            if (galleryRoot.isDirectory) galleryRoot.copyRecursively(rollbackGallery, overwrite = true)

            try {
                databaseFile.parentFile?.mkdirs()
                deleteDatabaseSidecars()
                stagedDb.copyTo(databaseFile, overwrite = true)
                galleryRoot.deleteRecursively()
                if (stagedGallery.isDirectory) stagedGallery.copyRecursively(galleryRoot, overwrite = true)
                else galleryRoot.mkdirs()
                deleteDatabaseSidecars()
            } catch (error: Throwable) {
                databaseFile.delete()
                if (rollbackDb.isFile) rollbackDb.copyTo(databaseFile, overwrite = true)
                galleryRoot.deleteRecursively()
                if (rollbackGallery.isDirectory) rollbackGallery.copyRecursively(galleryRoot, overwrite = true)
                deleteDatabaseSidecars()
                throw error
            } finally {
                rollback.deleteRecursively()
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun extractValidated(input: InputStream, stage: File) {
        var manifestFound = false
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                require(!entry.isDirectory || name.endsWith('/')) { "Invalid backup entry" }
                require(!name.startsWith('/') && !name.contains("../") && name != "..") { "Unsafe backup path" }
                require(name == MANIFEST_ENTRY || name == DATABASE_ENTRY || name.startsWith(GALLERY_PREFIX)) {
                    "Unexpected backup entry: $name"
                }
                if (name == MANIFEST_ENTRY) {
                    val text = zip.readBytesLimited(MAX_MANIFEST_BYTES).toString(Charsets.UTF_8)
                    require(text.lineSequence().any { it.trim() == "format=$FORMAT_VERSION" }) { "Unsupported backup format" }
                    manifestFound = true
                } else if (!entry.isDirectory) {
                    val target = File(stage, name).canonicalFile
                    require(target.path.startsWith(stage.canonicalFile.path + File.separator)) { "Unsafe backup path" }
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { out -> zip.copyToLimited(out, MAX_ENTRY_BYTES) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        require(manifestFound) { "Backup manifest is missing" }
    }

    private fun validateSqlite(file: File) {
        require(file.length() >= SQLITE_HEADER.size) { "Save database is truncated" }
        val header = ByteArray(SQLITE_HEADER.size)
        file.inputStream().use { input -> require(input.read(header) == header.size) { "Save database is truncated" } }
        require(header.contentEquals(SQLITE_HEADER)) { "Backup database is not SQLite" }
    }

    private fun deleteDatabaseSidecars() {
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        File(databaseFile.path + "-journal").delete()
    }

    private fun writeText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun ZipInputStream.readBytesLimited(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        copyToLimited(out, limit)
        return out.toByteArray()
    }

    private fun InputStream.copyToLimited(output: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Backup entry is too large" }
            output.write(buffer, 0, read)
        }
    }

    companion object {
        private const val DATABASE_NAME = "game_broth.db"
        private const val GALLERY_DIR = "staff_gallery"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_ENTRY = "manifest.txt"
        private const val DATABASE_ENTRY = "database/game_broth.db"
        private const val GALLERY_PREFIX = "staff_gallery/"
        private const val MAX_MANIFEST_BYTES = 16_384L
        private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
