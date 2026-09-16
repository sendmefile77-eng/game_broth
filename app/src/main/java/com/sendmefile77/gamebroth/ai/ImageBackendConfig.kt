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

    /** Absolute path of the Q4 diffusion GGUF after import. */
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
            val pendingSource = persisted?.takeIf { it.startsWith("content://") }
            modelUri = persisted?.takeUnless { it.startsWith("content://") }

            val diffusion = modelUri?.let(::File)
            when {
                diffusion == null -> Unit
                !diffusion.isFile -> {
                    modelUri = null
                    persistModel(app, null)
                }
                MobileImageModelPolicy.diffusionValidationError(diffusion) != null -> {
                    // Old builds could retain Q8/F16/safetensors in app-private storage. Remove
                    // only an invalid diffusion file; missing CLIP/VAE must never delete valid Q4.
                    val modelsDir = File(app.filesDir, "models")
                    if (diffusion.parentFile == modelsDir) diffusion.delete()
                    modelUri = null
                    persistModel(app, null)
                }
            }

            modelImportStatus = packStatus()
            initialized = true

            // Resume an import persisted by an older build. The Q4 path itself stays null until
            // the copy is complete so native generation can never receive a content:// URI.
            pendingSource?.let { startImport(app, it) }
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
     * One picker is used for the whole split SDXL pack. The selected file is identified as Q4
     * diffusion, CLIP-L, CLIP-G or VAE and copied into the private models directory. The already
     * imported Q4 file is preserved while the remaining components are added one by one.
     */
    fun setModelUri(context: Context, value: String?) {
        initialize(context)
        val app = context.applicationContext
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            modelUri = null
            modelImportStatus = "Q4 diffusion-модель не выбрана"
            persistModel(app, null)
            return
        }

        if (normalized.startsWith("content://")) {
            modelImportStatus = "проверяем файл комплекта…"
            startImport(app, normalized)
            return
        }

        // Direct filesystem paths are supported for the Q4 diffusion file. Accessory components
        // selected through Android's document picker are copied to canonical sibling paths.
        val file = File(normalized)
        val problem = when {
            !file.isFile -> "файл модели не найден"
            else -> MobileImageModelPolicy.diffusionValidationError(file)
        }
        if (problem == null) {
            modelUri = normalized
            persistModel(app, normalized)
            modelImportStatus = packStatus()
        } else {
            modelImportStatus = problem
        }
    }

    fun label(): String = when (mode) {
        ImageBackendMode.LOCAL_DREAM -> "Local Dream"
        ImageBackendMode.EMBEDDED -> "Встроенный stable-diffusion.cpp"
    }

    private fun startImport(context: Context, source: String) {
        synchronized(this) {
            if (importingSource == source) return
            if (importingSource != null) {
                modelImportStatus = "Дождитесь завершения текущего импорта файла модели."
                return
            }
            importingSource = source
        }

        Thread({
            try {
                val uri = Uri.parse(source)
                val name = queryName(context, uri)
                val expectedSize = querySize(context, uri)
                val component = MobileImageModelPolicy.detectComponent(name, expectedSize)
                    ?: error("Не удалось определить этот файл. Нужны Q4_K_M GGUF, CLIP-L, CLIP-G или VAE.")
                MobileImageModelPolicy.componentValidationError(component, name, expectedSize)?.let { error(it) }

                modelImportStatus = "копируем ${component.displayName} в хранилище игры…"
                val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(180)
                val target = if (component == MobileImageModelComponent.DIFFUSION) {
                    File(modelsDir, safeName)
                } else {
                    MobileImageModelPolicy.canonicalFile(component, modelsDir)
                }
                val temp = File(modelsDir, target.name + ".part")

                if (!(target.isFile && expectedSize != null && target.length() == expectedSize)) {
                    temp.delete()
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "не удалось открыть выбранный файл" }
                        temp.outputStream().buffered(8 * 1024 * 1024).use { output ->
                            input.copyTo(output, 8 * 1024 * 1024)
                        }
                    }
                    if (expectedSize != null && temp.length() != expectedSize) {
                        temp.delete()
                        error("копирование оборвалось: ожидалось $expectedSize байт, получено ${temp.length()}")
                    }
                    MobileImageModelPolicy.componentValidationError(component, target.name, temp.length())?.let { problem ->
                        temp.delete()
                        error(problem)
                    }
                    if (target.exists() && !target.delete()) error("не удалось заменить старую копию ${component.displayName}")
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }

                MobileImageModelPolicy.componentValidationError(component, target.name, target.length())?.let { problem ->
                    target.delete()
                    error(problem)
                }

                if (component == MobileImageModelComponent.DIFFUSION) {
                    val previous = modelUri?.takeIf { !it.startsWith("content://") }?.let(::File)
                    modelUri = target.absolutePath
                    persistModel(context, target.absolutePath)
                    if (previous != null && previous.parentFile == modelsDir && previous != target) previous.delete()
                }
                modelImportStatus = packStatus()
            } catch (error: Throwable) {
                modelImportStatus = "импорт не удался: ${error.message ?: error::class.java.simpleName}"
            } finally {
                synchronized(this) { importingSource = null }
            }
        }, "GameBroth-model-import").apply {
            isDaemon = true
            start()
        }
    }

    private fun packStatus(): String {
        val path = modelUri
        if (path.isNullOrBlank()) {
            return "Добавьте ${MobileImageModelPolicy.RECOMMENDED_MODEL}. Затем той же кнопкой добавьте CLIP-L, CLIP-G и VAE."
        }
        val diffusion = File(path)
        if (!diffusion.isFile) return "Q4 diffusion-файл не найден"
        MobileImageModelPolicy.diffusionValidationError(diffusion)?.let { return it }
        val packProblem = MobileImageModelPolicy.validationError(diffusion)
        return packProblem ?: MobileImageModelPolicy.readyStatus(diffusion)
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
            ?: "selected_image_component"
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }?.takeIf { it > 0L }
    }
}
