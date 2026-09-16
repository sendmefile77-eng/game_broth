package com.sendmefile77.gamebroth.ai

import com.sendmefile77.gamebroth.aiimage.ImageAiStatus
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.ImageGenerationResult
import com.sendmefile77.gamebroth.aiimage.ImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Entry point for the future in-process stable-diffusion.cpp backend.
 *
 * The Kotlin side is intentionally usable before the native library lands: settings can persist the
 * chosen backend and model now, while status() reports exactly which native piece is still missing.
 * Local Dream remains fully available through LocalDreamClient as the fallback backend.
 */
class EmbeddedDiffusionClient : ImageGenerator {
    private val mutex = Mutex()

    override suspend fun status(force: Boolean): ImageAiStatus = withContext(Dispatchers.IO) {
        val modelUri = ImageBackendConfig.modelUri
        when {
            modelUri.isNullOrBlank() -> ImageAiStatus(false, "выберите локальную модель .safetensors/.gguf")
            !NativeDiffusionBridge.available -> ImageAiStatus(
                false,
                "модель выбрана; native stable-diffusion.cpp ещё не подключён (${NativeDiffusionBridge.detail})",
            )
            else -> ImageAiStatus(true, "embedded")
        }
    }

    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult? = mutex.withLock {
        withContext(Dispatchers.Default) {
            val modelUri = ImageBackendConfig.modelUri ?: error("Для встроенного генератора не выбрана модель")
            check(NativeDiffusionBridge.available) {
                "Встроенный stable-diffusion.cpp ещё не собран: ${NativeDiffusionBridge.detail}"
            }
            error("Native bridge загружен, но генерация ещё не подключена для $modelUri")
        }
    }
}

private object NativeDiffusionBridge {
    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("gamebroth_diffusion")
    }.exceptionOrNull()

    val available: Boolean
        get() = loadFailure == null

    val detail: String
        get() = loadFailure?.message ?: "native library loaded"
}
