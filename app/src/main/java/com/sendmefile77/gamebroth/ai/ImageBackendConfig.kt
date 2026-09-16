package com.sendmefile77.gamebroth.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.ArrayDeque

enum class ImageBackendMode {
    LOCAL_DREAM,
    EMBEDDED,
}

object ImageBackendConfig {
    private const val PREFS = "image_backend_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_MODEL_URI = "model_uri"
    private const val KEY_LAST_RUNTIME_ERROR = "last_runtime_error"

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

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
    var lastRuntimeError: String? = null
        private set

    @Volatile
    private var importingSource: String? = null

    private val importQueue = ArrayDeque<String>()

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mode = runCatching {
                ImageBackendMode.valueOf(prefs.getString(KEY_MODE, ImageBackendMode.LOCAL_DREAM.name).orEmpty())
            }.getOrDefault(ImageBackendMode.LOCAL_DREAM)
            lastRuntimeError = prefs.getString(KEY_LAST_RUNTIME_ERROR, null)?.takeIf { it.isNotBlank() }

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
                    val modelsDir = File(app.filesDir, "models")
                    if (diffusion.parentFile == modelsDir) diffusion.delete()
                    modelUri = null
                    persistModel(app, null)
                }
            }

            modelImportStatus = visibleStatus()
            initialized = true
            pendingSource?.let { enqueueImports(app, listOf(it)) }
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
            setModelUris(app, listOf(normalized))
            return
        }

        clearRuntimeError(app)
        val file = File(normalized)
        val problem = when {
            !file.isFile -> "файл модели не найден"
            else -> MobileImageModelPolicy.diffusionValidationError(file)
        }
        if (problem == null) {
            modelUri = normalized
            persistModel(app, normalized)
            modelImportStatus = visibleStatus()
        } else {
            modelImportStatus = problem
        }
    }

    /** Import all selected SDXL components from a single Android multi-document picker. */
    fun setModelUris(context: Context, values: List<String>) {
        initialize(context)
        val app = context.applicationContext
        val sources = values.asSequence()
            .map(String::trim)
            .filter { it.startsWith("content://") }
            .distinct()
            .toList()
        if (sources.isEmpty()) return
        clearRuntimeError(app)
        enqueueImports(app, sources)
    }

    /** Keep the real native failure visible even after leaving the generation screen or process restart. */
    fun reportRuntimeError(context: Context, detail: String) {
        initialize(context)
        val normalized = detail.trim().take(2_000).ifBlank { "неизвестная ошибка native-генератора" }
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
        if (initialized && importingSource == null) modelImportStatus = packStatus()
    }

    fun clearRuntimeError() {
        appContext?.let(::clearRuntimeError)
    }

    fun label(): String = when (mode) {
        ImageBackendMode.LOCAL_DREAM -> "Local Dream"
        ImageBackendMode.EMBEDDED -> "Встроенный stable-diffusion.cpp"
    }

    private fun enqueueImports(context: Context, sources: List<String>) {
        var startWorker = false
        synchronized(this) {
            sources.forEach { source ->
                if (source != importingSource && !importQueue.contains(source)) importQueue.addLast(source)
            }
            if (importingSource == null && importQueue.isNotEmpty()) {
                importingSource = "queued"
                startWorker = true
            }
            modelImportStatus = "В очереди файлов комплекта: ${importQueue.size + if (importingSource != null && importingSource != "queued") 1 else 0}"
        }
        if (startWorker) startImportWorker(context.applicationContext)
    }

    private fun startImportWorker(context: Context) {
        Thread({
            var firstFailure: String? = null
            while (true) {
                val source = synchronized(this) {
                    val next = if (importQueue.isEmpty()) null else importQueue.removeFirst()
                    importingSource = next
                    next
                } ?: break

                try {
                    importOne(context, source)
                } catch (error: Throwable) {
                    val detail = error.message ?: error::class.java.simpleName
                    if (firstFailure == null) firstFailure = detail
                    modelImportStatus = "импорт не удался: $detail"
                }
            }

            synchronized(this) { importingSource = null }
            val pack = packStatus()
            modelImportStatus = if (pack.startsWith("Q4-комплект готов")) {
                pack
            } else if (firstFailure != null) {
                "$pack · Ошибка импорта: $firstFailure"
            } else {
                visibleStatus()
            }
        }, "GameBroth-model-pack-import").apply {
            isDaemon = true
            start()
        }
    }

    private fun importOne(context: Context, source: String) {
        val uri = Uri.parse(source)
        val name = queryName(context, uri)
        val expectedSize = querySize(context, uri)
        val component = MobileImageModelPolicy.detectComponent(name, expectedSize)
            ?: error("Не удалось определить $name. Нужны Q4_K_M GGUF, CLIP-L, CLIP-G или VAE.")
        MobileImageModelPolicy.componentValidationError(component, name, expectedSize)?.let { error(it) }

        val pendingCount = synchronized(this) { importQueue.size }
        modelImportStatus = "копируем ${component.displayName}${if (pendingCount > 0) " · ещё $pendingCount" else ""}…"
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
    }

    private fun visibleStatus(): String = lastRuntimeError?.let { "Ошибка генерации: $it" } ?: packStatus()

    private fun packStatus(): String {
        val path = modelUri
        if (path.isNullOrBlank()) {
            return "Добавьте ${MobileImageModelPolicy.RECOMMENDED_MODEL}, CLIP-L, CLIP-G и VAE. Можно выбрать весь комплект сразу."
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
