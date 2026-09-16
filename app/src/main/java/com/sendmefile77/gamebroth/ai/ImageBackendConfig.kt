package com.sendmefile77.gamebroth.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

enum class ImageBackendMode {
    LOCAL_DREAM,
    EMBEDDED,
}

object ImageBackendConfig {
    private const val PREFS = "image_backend_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_MODEL_URI = "model_uri"

    @Volatile
    private var initialized = false

    @Volatile
    var mode: ImageBackendMode = ImageBackendMode.LOCAL_DREAM
        private set

    /** Absolute internal file path when import is complete; content:// URI while copying. */
    @Volatile
    var modelUri: String? = null
        private set

    @Volatile
    var modelImportStatus: String = "модель не выбрана"
        private set

    @Volatile
    private var importingSource: String? = null

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mode = runCatching {
                ImageBackendMode.valueOf(prefs.getString(KEY_MODE, ImageBackendMode.LOCAL_DREAM.name).orEmpty())
            }.getOrDefault(ImageBackendMode.LOCAL_DREAM)

            val persisted = prefs.getString(KEY_MODEL_URI, null)?.takeIf { it.isNotBlank() }
            modelUri = persisted
            modelImportStatus = when {
                persisted.isNullOrBlank() -> "модель не выбрана"
                persisted.startsWith("content://") -> "импорт Q4-модели ожидает продолжения"
                else -> {
                    val file = File(persisted)
                    when {
                        !file.isFile -> {
                            modelUri = null
                            persistModel(app, null)
                            "файл модели не найден"
                        }
                        else -> {
                            val problem = MobileImageModelPolicy.validationError(file)
                            if (problem == null) {
                                MobileImageModelPolicy.readyStatus(file)
                            } else {
                                // Old builds allowed Q8/F16/safetensors. If such a checkpoint was
                                // copied into our private models directory, remove that private copy
                                // so an upgrade immediately returns the storage and cannot reload it.
                                val modelsDir = File(app.filesDir, "models")
                                if (file.parentFile == modelsDir) file.delete()
                                modelUri = null
                                persistModel(app, null)
                                problem
                            }
                        }
                    }
                }
            }
            initialized = true
            modelUri?.takeIf { it.startsWith("content://") }?.let { startImport(app, it) }
        }
    }

    fun setMode(context: Context, value: ImageBackendMode) {
        initialize(context)
        mode = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, value.name)
            .apply()
    }

    /**
     * The picker gives us a content:// URI. We immediately persist it and copy the selected Q4
     * GGUF on a background thread into app-private files/models. Native code then receives a real
     * filesystem path and never depends on a document-provider file descriptor.
     */
    fun setModelUri(context: Context, value: String?) {
        initialize(context)
        val app = context.applicationContext
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            modelUri = null
            modelImportStatus = "модель не выбрана"
            persistModel(app, null)
            return
        }

        if (normalized.startsWith("content://")) {
            modelUri = normalized
            modelImportStatus = "проверяем и копируем Q4 GGUF в хранилище игры…"
            persistModel(app, normalized)
            startImport(app, normalized)
        } else {
            val file = File(normalized)
            val problem = when {
                !file.isFile -> "файл модели не найден"
                else -> MobileImageModelPolicy.validationError(file)
            }
            if (problem == null) {
                modelUri = normalized
                modelImportStatus = MobileImageModelPolicy.readyStatus(file)
                persistModel(app, normalized)
            } else {
                modelUri = null
                modelImportStatus = problem
                persistModel(app, null)
            }
        }
    }

    fun label(): String = when (mode) {
        ImageBackendMode.LOCAL_DREAM -> "Local Dream"
        ImageBackendMode.EMBEDDED -> "Встроенный stable-diffusion.cpp"
    }

    private fun startImport(context: Context, source: String) {
        synchronized(this) {
            if (importingSource == source) return
            importingSource = source
        }
        Thread({
            try {
                val uri = Uri.parse(source)
                val name = queryName(context, uri)
                val expectedSize = querySize(context, uri)
                MobileImageModelPolicy.validationError(name, expectedSize)?.let { error(it) }

                val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(180)
                val target = File(modelsDir, safeName)
                val temp = File(modelsDir, "$safeName.part")

                if (!(target.isFile && expectedSize != null && target.length() == expectedSize)) {
                    temp.delete()
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "не удалось открыть выбранную модель" }
                        temp.outputStream().buffered(8 * 1024 * 1024).use { output ->
                            input.copyTo(output, 8 * 1024 * 1024)
                        }
                    }
                    if (expectedSize != null && temp.length() != expectedSize) {
                        temp.delete()
                        error("копирование модели оборвалось: ожидалось $expectedSize байт, получено ${temp.length()}")
                    }
                    MobileImageModelPolicy.validationError(safeName, temp.length())?.let { problem ->
                        temp.delete()
                        error(problem)
                    }
                    if (target.exists() && !target.delete()) error("не удалось заменить старую копию модели")
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }

                MobileImageModelPolicy.validationError(target)?.let { problem ->
                    target.delete()
                    error(problem)
                }

                val previous = modelUri?.takeIf { !it.startsWith("content://") }?.let(::File)
                modelUri = target.absolutePath
                modelImportStatus = MobileImageModelPolicy.readyStatus(target)
                persistModel(context, target.absolutePath)
                if (previous != null && previous.parentFile == modelsDir && previous != target) previous.delete()
            } catch (error: Throwable) {
                if (modelUri == source) {
                    modelUri = null
                    persistModel(context, null)
                }
                modelImportStatus = "импорт не удался: ${error.message ?: error::class.java.simpleName}"
            } finally {
                synchronized(this) { importingSource = null }
            }
        }, "GameBroth-model-import").apply {
            isDaemon = true
            start()
        }
    }

    private fun persistModel(context: Context, value: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_URI, value)
            .apply()
    }

    private fun queryName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "selected_image_model.gguf"
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }?.takeIf { it > 0L }
    }
}
