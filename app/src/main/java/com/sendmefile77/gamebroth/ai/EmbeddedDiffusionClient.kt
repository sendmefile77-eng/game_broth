package com.sendmefile77.gamebroth.ai

import android.graphics.Bitmap
import com.sendmefile77.gamebroth.aiimage.ImageAiStatus
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.ImageGenerationResult
import com.sendmefile77.gamebroth.aiimage.ImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/** In-process stable-diffusion.cpp backend. Local Dream remains available as a separate fallback. */
class EmbeddedDiffusionClient : ImageGenerator {
    private val mutex = Mutex()

    override suspend fun status(force: Boolean): ImageAiStatus = withContext(Dispatchers.IO) {
        val modelPath = ImageBackendConfig.modelUri
        when {
            modelPath.isNullOrBlank() -> ImageAiStatus(false, "выберите локальную модель .safetensors/.gguf")
            modelPath.startsWith("content://") -> ImageAiStatus(false, ImageBackendConfig.modelImportStatus)
            !File(modelPath).isFile -> ImageAiStatus(false, "файл модели не найден: $modelPath")
            !NativeDiffusionBridge.available -> ImageAiStatus(false, "native stable-diffusion.cpp не загрузился: ${NativeDiffusionBridge.detail}")
            else -> ImageAiStatus(true, "${File(modelPath).name}; ${NativeDiffusionBridge.runtimeInfo()}")
        }
    }

    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult? = mutex.withLock {
        withContext(Dispatchers.Default) {
            val modelPath = ImageBackendConfig.modelUri ?: error("Для встроенного генератора не выбрана модель")
            check(!modelPath.startsWith("content://")) { ImageBackendConfig.modelImportStatus }
            check(File(modelPath).isFile) { "Файл модели не найден. Выберите модель заново в настройках." }
            check(NativeDiffusionBridge.available) { "Встроенный stable-diffusion.cpp не загрузился: ${NativeDiffusionBridge.detail}" }

            val (width, height) = fitPhoneDimensions(request.width, request.height)
            val steps = request.steps.coerceIn(8, 24)
            val cfg = request.cfgScale.toFloat().coerceIn(3.0f, 8.0f)
            val started = System.currentTimeMillis()

            val loadError = NativeDiffusionBridge.loadModel(modelPath)
            check(loadError == null) { "Не удалось загрузить модель: $loadError" }
            try {
                val rgb = NativeDiffusionBridge.generateRgb(
                    prompt = request.prompt,
                    negativePrompt = request.negativePrompt,
                    width = width,
                    height = height,
                    steps = steps,
                    cfg = cfg,
                    seed = request.seed,
                ) ?: error("stable-diffusion.cpp: ${NativeDiffusionBridge.lastError()}")
                val png = encodeRgbToPng(rgb, width, height)
                ImageGenerationResult(
                    bytes = png,
                    seed = request.seed,
                    width = width,
                    height = height,
                    generationTimeMs = System.currentTimeMillis() - started,
                )
            } finally {
                // Important for future text+image alternation: never keep the heavy image model resident.
                NativeDiffusionBridge.unloadModel()
            }
        }
    }

    private fun fitPhoneDimensions(requestedWidth: Int, requestedHeight: Int): Pair<Int, Int> {
        val sourceW = requestedWidth.coerceAtLeast(256)
        val sourceH = requestedHeight.coerceAtLeast(256)
        val maxSide = maxOf(sourceW, sourceH)
        val scale = if (maxSide <= 1024) 1.0 else 1024.0 / maxSide.toDouble()
        fun aligned(value: Int): Int = ((value * scale / 64.0).roundToInt() * 64).coerceIn(512, 1024)
        return aligned(sourceW) to aligned(sourceH)
    }

    private fun encodeRgbToPng(rgb: ByteArray, width: Int, height: Int): ByteArray {
        val expected = width.toLong() * height.toLong() * 3L
        require(rgb.size.toLong() == expected) { "native renderer returned ${rgb.size} bytes, expected $expected" }
        val pixels = IntArray(width * height)
        var source = 0
        for (i in pixels.indices) {
            val r = rgb[source++].toInt() and 0xff
            val g = rgb[source++].toInt() and 0xff
            val b = rgb[source++].toInt() and 0xff
            pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "не удалось упаковать PNG" }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

private object NativeDiffusionBridge {
    private val loadFailure: Throwable? = runCatching { System.loadLibrary("gamebroth_diffusion") }.exceptionOrNull()

    val available: Boolean
        get() = loadFailure == null

    val detail: String
        get() = loadFailure?.message ?: "native library loaded"

    fun runtimeInfo(): String = if (!available) detail else runCatching { nativeRuntimeInfo() }.getOrElse { it.message ?: "native runtime" }
    fun loadModel(path: String): String? = nativeLoadModel(path)
    fun unloadModel() = nativeUnloadModel()
    fun lastError(): String = nativeLastError()

    fun generateRgb(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        seed: Long,
    ): ByteArray? = nativeGenerateRgb(prompt, negativePrompt, width, height, steps, cfg, seed)

    @JvmStatic private external fun nativeRuntimeInfo(): String
    @JvmStatic private external fun nativeLoadModel(modelPath: String): String?
    @JvmStatic private external fun nativeUnloadModel()
    @JvmStatic private external fun nativeLastError(): String
    @JvmStatic private external fun nativeGenerateRgb(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        seed: Long,
    ): ByteArray?
}
