package com.sendmefile77.gamebroth.ai

import com.sendmefile77.gamebroth.aitext.TextAiStatus
import com.sendmefile77.gamebroth.aitext.TextGenerationRequest
import com.sendmefile77.gamebroth.aitext.TextGenerationResult
import com.sendmefile77.gamebroth.aitext.TextNarrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Future in-process llama.cpp backend.
 *
 * The Kotlin lifecycle is complete now: validate model -> acquire the global AI slot -> load native
 * model -> generate -> unload in finally. The native library is intentionally added in the next
 * pass so Tellama remains a safe fallback while this architecture lands.
 */
class EmbeddedTextClient : TextNarrator {
    override suspend fun status(force: Boolean): TextAiStatus = withContext(Dispatchers.IO) {
        val modelPath = TextBackendConfig.modelPath
        when {
            modelPath.isNullOrBlank() -> TextAiStatus(false, detail = "выберите локальную модель .gguf")
            modelPath.startsWith("content://") -> TextAiStatus(false, detail = TextBackendConfig.modelImportStatus)
            !File(modelPath).isFile -> TextAiStatus(false, detail = "файл модели не найден: $modelPath")
            !NativeTextBridge.available -> TextAiStatus(
                false,
                model = File(modelPath).name,
                detail = "native llama.cpp ещё не подключён: ${NativeTextBridge.detail}",
            )
            else -> TextAiStatus(true, model = File(modelPath).name, detail = NativeTextBridge.runtimeInfo())
        }
    }

    override suspend fun generate(request: TextGenerationRequest): TextGenerationResult? = LocalAiResourceGate.withSlot {
        withContext(Dispatchers.Default) {
            val modelPath = TextBackendConfig.modelPath ?: error("Для встроенного текста не выбрана GGUF-модель")
            check(!modelPath.startsWith("content://")) { TextBackendConfig.modelImportStatus }
            check(File(modelPath).isFile) { "Файл текстовой модели не найден. Выберите GGUF заново в настройках." }
            check(NativeTextBridge.available) { "Встроенный llama.cpp не загрузился: ${NativeTextBridge.detail}" }

            val profile = TextBackendConfig.embeddedProfile()
            val started = System.currentTimeMillis()
            val loadError = NativeTextBridge.loadModel(
                path = modelPath,
                contextTokens = profile.contextTokens,
                threads = profile.threads,
                batchSize = profile.batchSize,
            )
            check(loadError == null) { "Не удалось загрузить текстовую модель: $loadError" }

            try {
                val prompt = buildPrompt(request)
                val maxTokens = request.maxTokens.coerceIn(128, profile.maxGeneratedTokens)
                val content = NativeTextBridge.generate(
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temperature = request.temperature.toFloat().coerceIn(0.0f, 2.0f),
                )?.trim().orEmpty()
                if (content.isBlank()) return@withContext null
                TextGenerationResult(
                    content = content,
                    model = "embedded:${File(modelPath).name}",
                    elapsedMs = System.currentTimeMillis() - started,
                )
            } finally {
                // Critical on a phone: release model + KV cache before diffusion acquires the slot.
                NativeTextBridge.unloadModel()
            }
        }
    }

    private fun buildPrompt(request: TextGenerationRequest): String = buildString {
        append("<SYSTEM>\n").append(request.systemPrompt.trim()).append("\n</SYSTEM>\n")
        append("<STATE>\n").append(request.stateDigest.trim()).append("\n</STATE>\n")
        if (request.recentEvents.isNotEmpty()) {
            append("<RECENT_EVENTS>\n")
            request.recentEvents.take(12).forEach {
                append("D").append(it.day).append(' ').append(it.type).append(": ").append(it.summary).append('\n')
            }
            append("</RECENT_EVENTS>\n")
        }
        append("<FACTS>\n").append(request.playerAction.trim()).append("\n</FACTS>\n")
        append("Use only the supplied facts. Never invent or alter game state, numbers, people, services, outcomes or consequences.\n")
        append("<ASSISTANT>\n")
    }
}

/** JNI contract for the next pass. The library is absent for now, so selecting Embedded fails safely. */
private object NativeTextBridge {
    private val loadFailure: Throwable? = runCatching { System.loadLibrary("gamebroth_llama") }.exceptionOrNull()

    val available: Boolean
        get() = loadFailure == null

    val detail: String
        get() = loadFailure?.message ?: "native library loaded"

    fun runtimeInfo(): String = if (!available) detail else runCatching { nativeRuntimeInfo() }.getOrElse { it.message ?: "llama.cpp runtime" }

    fun loadModel(path: String, contextTokens: Int, threads: Int, batchSize: Int): String? =
        nativeLoadModel(path, contextTokens, threads, batchSize)

    fun generate(prompt: String, maxTokens: Int, temperature: Float): String? =
        nativeGenerate(prompt, maxTokens, temperature)

    fun unloadModel() = nativeUnloadModel()

    @JvmStatic private external fun nativeRuntimeInfo(): String
    @JvmStatic private external fun nativeLoadModel(modelPath: String, contextTokens: Int, threads: Int, batchSize: Int): String?
    @JvmStatic private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String?
    @JvmStatic private external fun nativeUnloadModel()
}
