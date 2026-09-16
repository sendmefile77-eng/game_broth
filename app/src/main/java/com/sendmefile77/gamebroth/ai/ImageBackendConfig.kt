package com.sendmefile77.gamebroth.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

enum class ImageBackendMode {
    /** Kept only so old preferences remain readable. It now routes to the embedded runtime too. */
    LOCAL_DREAM,
    EMBEDDED,
}

/**
 * Configuration and one-time importer for the in-app Local Dream/QNN backend.
 *
 * The selected ZIP is never used in place. It is streamed once into the application's private
 * storage. Future launches use the extracted model directory directly, so there is no repeated
 * unpacking and no dependency on another Android application.
 *
 * Security rule: a model archive is DATA ONLY. Executable files and unknown payloads from the ZIP
 * are never installed. QNN runtime libraries come exclusively from this APK's trusted assets.
 */
object ImageBackendConfig {
    private const val PREFS = "image_backend_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_MODEL_URI = "model_uri"
    private const val KEY_PENDING_SOURCE = "pending_model_source"
    private const val KEY_LAST_RUNTIME_ERROR = "last_runtime_error"

    internal const val MODEL_DIR_NAME = "gamebroth_sdxl"
    internal const val RUNTIME_DIR_NAME = "local_dream_runtime"
    internal const val READY_MARKER = ".gamebroth_model_ready"

    private const val MAX_EXTRACTED_MODEL_BYTES = 8L * 1024 * 1024 * 1024

    private val requiredSdxlFiles = setOf(
        "tokenizer.json",
        "clip.mnn",
        "clip_2.mnn",
        "unet.bin",
        "vae_decoder.bin",
        "vae_encoder.bin",
        "pos_emb.bin",
        "token_emb.bin",
        "pos_emb_2.bin",
        "token_emb_2.bin",
    )

    private val allowedZipFiles = requiredSdxlFiles + setOf(
        // Known aliases used by some exported SDXL packs. They are normalized after extraction.
        "clip_l.mnn",
        "clip_g.mnn",
        "text_encoder.mnn",
        "text_encoder_2.mnn",
    )

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    /** The old two-mode preference is migrated to the single in-app backend. */
    @Volatile
    var mode: ImageBackendMode = ImageBackendMode.EMBEDDED
        private set

    /** Absolute path of the extracted Local Dream SDXL model directory. */
    @Volatile
    var modelUri: String? = null
        private set

    @Volatile
    var modelImportStatus: String = "QNN ZIP-модель не выбрана"
        private set

    @Volatile
    var lastRuntimeError: String? = null
        private set

    @Volatile
    private var importing = false

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            mode = ImageBackendMode.EMBEDDED
            prefs.edit().putString(KEY_MODE, ImageBackendMode.EMBEDDED.name).apply()

            lastRuntimeError = prefs.getString(KEY_LAST_RUNTIME_ERROR, null)?.takeIf { it.isNotBlank() }
            val persisted = prefs.getString(KEY_MODEL_URI, null)?.takeIf { it.isNotBlank() }
            val persistedDir = persisted?.let(::File)
            modelUri = if (persistedDir != null && modelValidationError(persistedDir) == null) {
                persistedDir.absolutePath
            } else {
                if (persisted != null) prefs.edit().remove(KEY_MODEL_URI).apply()
                null
            }

            modelImportStatus = visibleStatus()
            initialized = true

            prefs.getString(KEY_PENDING_SOURCE, null)
                ?.takeIf { it.startsWith("content://") }
                ?.let { startZipImport(app, it) }
        }
    }

    fun setMode(context: Context, value: ImageBackendMode) {
        initialize(context)
        mode = ImageBackendMode.EMBEDDED
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, ImageBackendMode.EMBEDDED.name)
            .apply()
    }

    fun setModelUri(context: Context, value: String?) {
        initialize(context)
        val app = context.applicationContext
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            modelUri = null
            modelImportStatus = "QNN ZIP-модель не выбрана"
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_MODEL_URI)
                .remove(KEY_PENDING_SOURCE)
                .apply()
            return
        }

        clearRuntimeError(app)
        if (normalized.startsWith("content://")) {
            startZipImport(app, normalized)
            return
        }

        val directory = File(normalized)
        val problem = modelValidationError(directory)
        if (problem == null) {
            modelUri = directory.absolutePath
            persistModel(app, directory.absolutePath)
            modelImportStatus = readyStatus(directory)
        } else {
            modelImportStatus = problem
        }
    }

    /** Compatibility entry point for the old multi-file picker: the new importer needs one ZIP. */
    fun setModelUris(context: Context, values: List<String>) {
        initialize(context)
        val source = values.asSequence().map(String::trim).firstOrNull { it.isNotBlank() } ?: return
        setModelUri(context, source)
    }

    fun reportRuntimeError(context: Context, detail: String) {
        initialize(context)
        val normalized = detail.trim().take(2_000).ifBlank { "неизвестная ошибка Local Dream/QNN" }
        lastRuntimeError = normalized
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_RUNTIME_ERROR, normalized)
            .apply()
        modelImportStatus = "Ошибка генерации: $normalized"
    }

    fun reportRuntimeError(detail: String) {
        appContext?.let { reportRuntimeError(it, detail) }
    }

    fun clearRuntimeError(context: Context) {
        val app = context.applicationContext
        appContext = app
        lastRuntimeError = null
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_RUNTIME_ERROR)
            .apply()
        if (initialized && !importing) modelImportStatus = packStatus()
    }

    fun clearRuntimeError() {
        appContext?.let(::clearRuntimeError)
    }

    fun label(): String = "Встроенный Local Dream / QNN"

    internal fun requireContext(): Context = appContext
        ?: error("ImageBackendConfig не инициализирован")

    internal fun runtimeDir(context: Context = requireContext()): File =
        File(context.applicationContext.filesDir, RUNTIME_DIR_NAME).apply { mkdirs() }

    internal fun extractedModelDir(context: Context = requireContext()): File? =
        modelUri?.let(::File)?.takeIf { modelValidationError(it) == null }

    internal fun modelValidationError(directory: File): String? {
        if (!directory.isDirectory) return "Распакованная модель не найдена"
        val missing = requiredSdxlFiles.filterNot { File(directory, it).isFile }
        if (missing.isNotEmpty()) {
            return "QNN ZIP неполный: нет ${missing.joinToString()}"
        }
        return null
    }

    private fun startZipImport(context: Context, source: String) {
        synchronized(this) {
            if (importing) {
                modelImportStatus = "Импорт уже идёт. Дождитесь завершения распаковки ZIP."
                return
            }
            importing = true
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING_SOURCE, source)
            .apply()
        modelImportStatus = "Проверяем и распаковываем QNN ZIP…"

        Thread({
            try {
                importZip(context, source)
            } catch (error: Throwable) {
                val detail = error.message ?: error::class.java.simpleName
                modelImportStatus = "Импорт ZIP не удался: $detail"
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_PENDING_SOURCE)
                    .apply()
            } finally {
                importing = false
            }
        }, "GameBroth-qnn-zip-import").apply {
            isDaemon = true
            start()
        }
    }

    private fun importZip(context: Context, source: String) {
        val uri = Uri.parse(source)
        val displayName = queryName(context, uri)
        require(displayName.endsWith(".zip", ignoreCase = true)) {
            "выберите один ZIP-комплект Local Dream/QNN, а не отдельные GGUF-файлы"
        }

        val filesDir = context.filesDir
        val modelsRoot = File(filesDir, "models").apply { mkdirs() }
        val stageRoot = File(filesDir, "image_import_stage").apply {
            deleteRecursively()
            mkdirs()
        }
        val modelStage = File(stageRoot, "model").apply { mkdirs() }

        var extractedBytes = 0L
        var extractedEntries = 0
        val seenNames = mutableSetOf<String>()

        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "Android не смог открыть выбранный ZIP" }
            ZipInputStream(BufferedInputStream(raw, 8 * 1024 * 1024)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val rawName = entry.name.replace('\\', '/')
                    require(rawName.split('/').none { it == ".." }) { "опасный путь внутри ZIP: $rawName" }
                    val leaf = rawName.substringAfterLast('/').trim()
                    if (leaf.isBlank() || leaf !in allowedZipFiles) {
                        // In particular, never accept libQnn*.so, APK/JAR/DEX files, scripts, or any
                        // other executable/unknown payload from a model archive.
                        zip.closeEntry()
                        continue
                    }
                    require(seenNames.add(leaf)) { "ZIP содержит дубликат файла модели: $leaf" }

                    val target = File(modelStage, leaf)
                    FileOutputStream(target, false).buffered(8 * 1024 * 1024).use { output ->
                        val buffer = ByteArray(8 * 1024 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read <= 0) break
                            extractedBytes += read
                            require(extractedBytes <= MAX_EXTRACTED_MODEL_BYTES) {
                                "модель после распаковки превышает безопасный предел 8 ГБ"
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                    extractedEntries++
                    if (extractedEntries % 3 == 0) {
                        modelImportStatus = "Распаковываем QNN-модель · ${humanSize(extractedBytes)}…"
                    }
                    zip.closeEntry()
                }
            }
        }
        require(extractedEntries > 0) { "ZIP не содержит поддерживаемых файлов SDXL/QNN" }

        normalizeKnownAliases(modelStage)
        modelValidationError(modelStage)?.let { error(it) }
        File(modelStage, "SDXL").writeText("GameBroth embedded Local Dream/QNN\n")
        File(modelStage, READY_MARKER).writeText(System.currentTimeMillis().toString())

        val target = File(modelsRoot, MODEL_DIR_NAME)
        val backup = File(modelsRoot, "$MODEL_DIR_NAME.previous")
        backup.deleteRecursively()
        if (target.exists() && !target.renameTo(backup)) {
            error("не удалось подготовить замену старой модели")
        }
        if (!modelStage.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            error("не удалось перенести распакованную модель в приватное хранилище игры")
        }
        backup.deleteRecursively()

        // QNN runtime is deliberately NOT accepted from the model ZIP. The only executable
        // libraries used by the image backend are the QAIRT files embedded into the APK at build.
        modelUri = target.absolutePath
        persistModel(context, target.absolutePath)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENDING_SOURCE)
            .apply()
        clearRuntimeError(context)
        modelImportStatus = readyStatus(target)
        stageRoot.deleteRecursively()
    }

    private fun normalizeKnownAliases(directory: File) {
        fun alias(from: String, to: String) {
            val source = File(directory, from)
            val target = File(directory, to)
            if (source.isFile && !target.exists()) source.copyTo(target)
        }
        alias("clip_l.mnn", "clip.mnn")
        alias("clip_g.mnn", "clip_2.mnn")
        alias("text_encoder.mnn", "clip.mnn")
        alias("text_encoder_2.mnn", "clip_2.mnn")
    }

    private fun visibleStatus(): String = lastRuntimeError?.let { "Ошибка генерации: $it" } ?: packStatus()

    private fun packStatus(): String {
        val path = modelUri ?: return "Добавьте один ZIP-комплект SDXL QNN 2.48. Он распакуется один раз."
        val directory = File(path)
        return modelValidationError(directory) ?: readyStatus(directory)
    }

    private fun readyStatus(directory: File): String =
        "QNN-комплект готов · ZIP распакован · ${directory.name} · встроенный Local Dream/QNN 2.48"

    private fun persistModel(context: Context, value: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (value == null) remove(KEY_MODEL_URI) else putString(KEY_MODEL_URI, value)
            }
            .apply()
    }

    private fun queryName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "selected_qnn_model.zip"
    }

    private fun humanSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f ГБ".format(bytes.toDouble() / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.0f МБ".format(bytes.toDouble() / (1024.0 * 1024))
        else -> "$bytes Б"
    }
}
