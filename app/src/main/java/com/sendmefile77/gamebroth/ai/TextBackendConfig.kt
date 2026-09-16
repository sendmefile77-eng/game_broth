package com.sendmefile77.gamebroth.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

enum class TextBackendMode {
    TELLAMA,
    EMBEDDED,
}

data class EmbeddedTextProfile(
    val contextTokens: Int = 8192,
    val threads: Int = 6,
    val batchSize: Int = 256,
    val maxGeneratedTokens: Int = 1800,
)

/**
 * Persistent text-backend selection and local GGUF model import.
 *
 * Model weights never live in the APK or repository. The Android document picker gives us a
 * content:// URI, then the file is copied in the background into app-private files/models/text so
 * llama.cpp will receive a normal filesystem path in the native pass.
 */
object TextBackendConfig {
    private const val PREFS = "text_backend_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_MODEL_PATH = "model_path"
    private const val KEY_CONTEXT_TOKENS = "context_tokens"
    private const val KEY_THREADS = "threads"
    private const val KEY_BATCH_SIZE = "batch_size"
    private const val KEY_MAX_GENERATED_TOKENS = "max_generated_tokens"

    @Volatile private var initialized = false
    @Volatile private var importingSource: String? = null

    @Volatile var mode: TextBackendMode = TextBackendMode.TELLAMA
        private set

    /** Absolute app-private path when import is complete; content:// while a copy is pending. */
    @Volatile var modelPath: String? = null
        private set

    @Volatile var modelImportStatus: String = "модель не выбрана"
        private set

    @Volatile private var profile = EmbeddedTextProfile()

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mode = runCatching {
                TextBackendMode.valueOf(prefs.getString(KEY_MODE, TextBackendMode.TELLAMA.name).orEmpty())
            }.getOrDefault(TextBackendMode.TELLAMA)
            modelPath = prefs.getString(KEY_MODEL_PATH, null)?.takeIf { it.isNotBlank() }
            profile = EmbeddedTextProfile(
                contextTokens = prefs.getInt(KEY_CONTEXT_TOKENS, 8192).coerceIn(2048, 16384),
                threads = prefs.getInt(KEY_THREADS, 6).coerceIn(2, 12),
                batchSize = prefs.getInt(KEY_BATCH_SIZE, 256).coerceIn(64, 1024),
                maxGeneratedTokens = prefs.getInt(KEY_MAX_GENERATED_TOKENS, 1800).coerceIn(256, 4096),
            )
            modelImportStatus = when {
                modelPath.isNullOrBlank() -> "модель не выбрана"
                modelPath!!.startsWith("content://") -> "импорт модели ожидает продолжения"
                File(modelPath!!).isFile -> "модель готова: ${File(modelPath!!).name}"
                else -> "файл модели не найден"
            }
            initialized = true
            modelPath?.takeIf { it.startsWith("content://") }?.let { startImport(app, it) }
        }
    }

    fun setMode(context: Context, value: TextBackendMode) {
        initialize(context)
        mode = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value.name).apply()
    }

    fun setModelUri(context: Context, value: String?) {
        initialize(context)
        val app = context.applicationContext
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            modelPath = null
            modelImportStatus = "модель не выбрана"
            persistModel(app, null)
            return
        }

        if (normalized.startsWith("content://")) {
            modelPath = normalized
            modelImportStatus = "копируем GGUF в память игры…"
            persistModel(app, normalized)
            startImport(app, normalized)
        } else {
            modelPath = normalized
            modelImportStatus = if (File(normalized).isFile) "модель готова: ${File(normalized).name}" else "файл модели не найден"
            persistModel(app, normalized)
        }
    }

    fun embeddedProfile(): EmbeddedTextProfile = profile

    fun setEmbeddedProfile(context: Context, value: EmbeddedTextProfile) {
        initialize(context)
        val safe = value.copy(
            contextTokens = value.contextTokens.coerceIn(2048, 16384),
            threads = value.threads.coerceIn(2, 12),
            batchSize = value.batchSize.coerceIn(64, 1024),
            maxGeneratedTokens = value.maxGeneratedTokens.coerceIn(256, 4096),
        )
        profile = safe
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_CONTEXT_TOKENS, safe.contextTokens)
            .putInt(KEY_THREADS, safe.threads)
            .putInt(KEY_BATCH_SIZE, safe.batchSize)
            .putInt(KEY_MAX_GENERATED_TOKENS, safe.maxGeneratedTokens)
            .apply()
    }

    fun label(): String = when (mode) {
        TextBackendMode.TELLAMA -> "Tellama / Ollama-compatible"
        TextBackendMode.EMBEDDED -> "Встроенный llama.cpp"
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
                require(name.endsWith(".gguf", true)) { "для встроенной текстовой модели нужен файл .gguf" }
                val modelsDir = File(context.filesDir, "models/text").apply { mkdirs() }
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(180)
                val target = File(modelsDir, safeName)
                val temp = File(modelsDir, "$safeName.part")
                val expectedSize = querySize(context, uri)

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
                    if (target.exists() && !target.delete()) error("не удалось заменить старую копию модели")
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }

                val previous = modelPath?.takeIf { !it.startsWith("content://") }?.let(::File)
                modelPath = target.absolutePath
                modelImportStatus = "модель готова: ${target.name}"
                persistModel(context, target.absolutePath)
                if (previous != null && previous.parentFile == modelsDir && previous != target) previous.delete()
            } catch (error: Throwable) {
                modelImportStatus = "импорт не удался: ${error.message ?: error::class.java.simpleName}"
            } finally {
                synchronized(this) { importingSource = null }
            }
        }, "GameBroth-text-model-import").apply {
            isDaemon = true
            start()
        }
    }

    private fun persistModel(context: Context, value: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL_PATH, value).apply()
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }?.takeIf { it.isNotBlank() } ?: "model_${System.currentTimeMillis()}.gguf"

    private fun querySize(context: Context, uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }?.takeIf { it > 0L }
}
