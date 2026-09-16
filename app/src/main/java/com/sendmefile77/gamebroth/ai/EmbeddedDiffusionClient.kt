package com.sendmefile77.gamebroth.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sendmefile77.gamebroth.aiimage.ImageAiStatus
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.ImageGenerationResult
import com.sendmefile77.gamebroth.aiimage.ImageGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/** In-process stable-diffusion.cpp backend. Local Dream remains a separate selectable backend. */
class EmbeddedDiffusionClient : ImageGenerator {
    private val mutex = Mutex()
    private var loadedModelPath: String? = null

    override suspend fun status(force: Boolean): ImageAiStatus = withContext(Dispatchers.IO) {
        val modelPath = ImageBackendConfig.modelUri
        when {
            modelPath.isNullOrBlank() -> ImageAiStatus(false, "выберите мобильную Q4 GGUF-модель")
            modelPath.startsWith("content://") -> ImageAiStatus(false, ImageBackendConfig.modelImportStatus)
            !File(modelPath).isFile -> ImageAiStatus(false, "файл модели не найден: $modelPath")
            MobileImageModelPolicy.validationError(File(modelPath)) != null -> ImageAiStatus(
                false,
                MobileImageModelPolicy.validationError(File(modelPath))!!,
            )
            !NativeDiffusionBridge.available -> ImageAiStatus(false, "native stable-diffusion.cpp не загрузился: ${NativeDiffusionBridge.detail}")
            else -> ImageAiStatus(true, "${File(modelPath).name}; ${NativeDiffusionBridge.runtimeInfo()}")
        }
    }

    /** One-off generation keeps the old RAM-safe behaviour and releases the model afterwards. */
    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult? = try {
        generateInSession(request)
    } finally {
        releaseModel()
    }

    /** Used by a portrait queue: the same loaded native context is reused until releaseModel(). */
    internal suspend fun generateInSession(request: ImageGenerationRequest): ImageGenerationResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val modelPath = validateModelPath()
            val (requestedWidth, requestedHeight) = fitPhoneDimensions(request.width, request.height)
            val steps = request.steps.coerceIn(4, FAST_STEPS)
            val cfg = FAST_CFG
            val seed = request.seed
            val started = System.currentTimeMillis()
            Log.i(TAG, "GEN start key=${request.cacheKey} size=${requestedWidth}x$requestedHeight steps=$steps cfg=$cfg seed=$seed")

            try {
                // A new attempt owns the diagnostic slot. If it fails, the exact exception/native
                // detail below is persisted and remains visible in Settings even after navigation.
                ImageBackendConfig.clearRuntimeError()
                ensureModelLoaded(modelPath, requestedWidth, requestedHeight, seed)
                ImageGenerationProgressStore.preparing(requestedWidth, requestedHeight, seed)

                val nativeImage = coroutineScope {
                    val renderJob = async(Dispatchers.Default) {
                        NativeDiffusionBridge.generateRgb(
                            prompt = request.prompt,
                            negativePrompt = request.negativePrompt,
                            width = requestedWidth,
                            height = requestedHeight,
                            steps = steps,
                            cfg = cfg,
                            seed = seed,
                        )
                    }
                    while (!renderJob.isCompleted) {
                        publishNativeProgress(requestedWidth, requestedHeight, seed)
                        delay(150)
                    }
                    renderJob.await()
                } ?: error("stable-diffusion.cpp: ${NativeDiffusionBridge.lastError()}")

                publishNativeProgress(requestedWidth, requestedHeight, seed)
                val actualWidth = nativeImage.width
                val actualHeight = nativeImage.height
                require(actualWidth > 0 && actualHeight > 0) {
                    "native renderer returned invalid dimensions ${actualWidth}x$actualHeight"
                }
                val expectedRgb = actualWidth.toLong() * actualHeight.toLong() * 3L
                require(nativeImage.bytes.size.toLong() == expectedRgb) {
                    "native renderer returned ${nativeImage.bytes.size} RGB bytes, expected $expectedRgb"
                }
                Log.i(TAG, "RGB received: ${nativeImage.bytes.size} bytes (${actualWidth}x$actualHeight)")

                ImageGenerationProgressStore.rgbReceived(actualWidth, actualHeight, seed)
                ImageGenerationProgressStore.encoding(actualWidth, actualHeight, seed)
                val png = encodeRgbToPng(nativeImage.bytes, actualWidth, actualHeight)
                validateEncodedPng(png, actualWidth, actualHeight)
                Log.i(TAG, "PNG encoded: ${png.size} bytes")

                ImageGenerationResult(
                    bytes = png,
                    seed = seed,
                    width = actualWidth,
                    height = actualHeight,
                    generationTimeMs = System.currentTimeMillis() - started,
                )
            } catch (cancelled: CancellationException) {
                Log.w(TAG, "GEN cancelled key=${request.cacheKey}")
                throw cancelled
            } catch (error: Throwable) {
                val detail = error.message ?: error::class.java.simpleName
                Log.e(TAG, "GEN failed key=${request.cacheKey}: $detail", error)
                ImageBackendConfig.reportRuntimeError(detail)
                ImageGenerationProgressStore.failed("Не удалось создать портрет", detail)
                throw error
            }
        }
    }

    internal suspend fun releaseModel() = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (loadedModelPath != null || NativeDiffusionBridge.isModelLoaded()) {
                Log.i(TAG, "MODEL unload requested")
                NativeDiffusionBridge.unloadModel()
            }
            loadedModelPath = null
        }
    }

    private suspend fun ensureModelLoaded(
        modelPath: String,
        width: Int,
        height: Int,
        seed: Long,
    ) {
        if (loadedModelPath == modelPath && NativeDiffusionBridge.isModelLoaded()) {
            Log.i(TAG, "MODEL reuse: ${File(modelPath).name}")
            return
        }
        if (loadedModelPath != null || NativeDiffusionBridge.isModelLoaded()) {
            NativeDiffusionBridge.unloadModel()
            loadedModelPath = null
        }

        ImageGenerationProgressStore.loading(width = width, height = height, seed = seed)
        Log.i(TAG, "MODEL load start: ${File(modelPath).name}")
        val loadError = coroutineScope {
            val loadJob = async(Dispatchers.Default) { NativeDiffusionBridge.loadModel(modelPath) }
            while (!loadJob.isCompleted) {
                publishNativeProgress(width, height, seed)
                delay(150)
            }
            loadJob.await()
        }
        check(loadError == null) { "Не удалось загрузить модель: $loadError" }
        check(NativeDiffusionBridge.isModelLoaded()) { "Native-контекст модели не создан" }
        loadedModelPath = modelPath
        Log.i(TAG, "MODEL load end: ${File(modelPath).name}")
    }

    private fun publishNativeProgress(width: Int, height: Int, seed: Long) {
        val snapshot = NativeDiffusionBridge.progress()
        when (snapshot.stage) {
            NativeDiffusionStage.LOADING_MODEL -> ImageGenerationProgressStore.loading(
                step = snapshot.step,
                total = snapshot.total,
                width = width,
                height = height,
                seed = seed,
            )
            NativeDiffusionStage.PREPARING -> ImageGenerationProgressStore.preparing(width, height, seed)
            NativeDiffusionStage.DIFFUSION -> ImageGenerationProgressStore.diffusion(
                step = snapshot.step,
                totalSteps = snapshot.total,
                width = width,
                height = height,
                seed = seed,
            )
            NativeDiffusionStage.VAE_DECODE -> ImageGenerationProgressStore.decoding(
                step = snapshot.step,
                total = snapshot.total,
                width = width,
                height = height,
                seed = seed,
            )
            NativeDiffusionStage.RGB_TRANSFER -> ImageGenerationProgressStore.rgbReceived(width, height, seed)
            else -> Unit
        }
    }

    private fun validateModelPath(): String {
        val modelPath = ImageBackendConfig.modelUri ?: error("Для встроенного генератора не выбрана Q4 GGUF-модель")
        check(!modelPath.startsWith("content://")) { ImageBackendConfig.modelImportStatus }
        val modelFile = File(modelPath)
        check(modelFile.isFile) { "Файл модели не найден. Выберите модель заново в настройках." }
        MobileImageModelPolicy.validationError(modelFile)?.let { error(it) }
        check(NativeDiffusionBridge.available) { "Встроенный stable-diffusion.cpp не загрузился: ${NativeDiffusionBridge.detail}" }
        return modelPath
    }

    private fun fitPhoneDimensions(requestedWidth: Int, requestedHeight: Int): Pair<Int, Int> {
        val sourceW = requestedWidth.coerceAtLeast(256)
        val sourceH = requestedHeight.coerceAtLeast(256)
        val maxSide = maxOf(sourceW, sourceH)
        val scale = if (maxSide <= FAST_MAX_SIDE) 1.0 else FAST_MAX_SIDE.toDouble() / maxSide.toDouble()
        fun aligned(value: Int): Int = ((value * scale / 64.0).roundToInt() * 64).coerceIn(512, FAST_MAX_SIDE)
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
                output.toByteArray().also { check(it.isNotEmpty()) { "PNG получился пустым" } }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun validateEncodedPng(bytes: ByteArray, width: Int, height: Int) {
        require(PngPayload.isPng(bytes)) { "Кодировщик вернул данные без PNG-сигнатуры" }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth == width && options.outHeight == height) {
            "PNG не декодируется с ожидаемым размером: ${options.outWidth}x${options.outHeight}, ожидалось ${width}x$height"
        }
    }

    private companion object {
        const val TAG = "PortraitPipeline"
        const val FAST_MAX_SIDE = 768
        const val FAST_STEPS = 8
        const val FAST_CFG = 1.0f
    }
}

internal data class NativeRgbImage(val bytes: ByteArray, val width: Int, val height: Int)

internal enum class NativeDiffusionStage(val code: Int) {
    IDLE(0),
    LOADING_MODEL(1),
    PREPARING(2),
    DIFFUSION(3),
    VAE_DECODE(4),
    RGB_TRANSFER(5),
    DONE(6),
    FAILED(7);

    companion object {
        fun fromCode(code: Int): NativeDiffusionStage = entries.firstOrNull { it.code == code } ?: IDLE
    }
}

internal data class NativeDiffusionProgress(
    val stage: NativeDiffusionStage,
    val step: Int,
    val total: Int,
)

internal object NativeDiffusionBridge {
    private val loadFailure: Throwable? = runCatching { System.loadLibrary("gamebroth_diffusion") }.exceptionOrNull()

    val available: Boolean
        get() = loadFailure == null

    val detail: String
        get() = loadFailure?.message ?: "native library loaded"

    fun runtimeInfo(): String = if (!available) detail else runCatching { nativeRuntimeInfo() }.getOrElse { it.message ?: "native runtime" }
    fun loadModel(path: String): String? = nativeLoadModel(path)
    fun unloadModel() {
        if (available) runCatching { nativeUnloadModel() }
    }
    fun isModelLoaded(): Boolean = available && runCatching { nativeIsModelLoaded() }.getOrDefault(false)
    fun lastError(): String = if (available) runCatching { nativeLastError() }.getOrDefault("unknown native error") else detail
    fun progress(): NativeDiffusionProgress = if (available) {
        NativeDiffusionProgress(
            stage = NativeDiffusionStage.fromCode(runCatching { nativeProgressStage() }.getOrDefault(0)),
            step = runCatching { nativeProgressStep() }.getOrDefault(0).coerceAtLeast(0),
            total = runCatching { nativeProgressTotal() }.getOrDefault(0).coerceAtLeast(0),
        )
    } else {
        NativeDiffusionProgress(NativeDiffusionStage.FAILED, 0, 0)
    }

    fun generateRgb(
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        seed: Long,
    ): NativeRgbImage? {
        val bytes = nativeGenerateRgb(prompt, negativePrompt, width, height, steps, cfg, seed) ?: return null
        return NativeRgbImage(bytes, nativeOutputWidth(), nativeOutputHeight())
    }

    @JvmStatic private external fun nativeRuntimeInfo(): String
    @JvmStatic private external fun nativeLoadModel(modelPath: String): String?
    @JvmStatic private external fun nativeUnloadModel()
    @JvmStatic private external fun nativeIsModelLoaded(): Boolean
    @JvmStatic private external fun nativeLastError(): String
    @JvmStatic private external fun nativeProgressStage(): Int
    @JvmStatic private external fun nativeProgressStep(): Int
    @JvmStatic private external fun nativeProgressTotal(): Int
    @JvmStatic private external fun nativeOutputWidth(): Int
    @JvmStatic private external fun nativeOutputHeight(): Int
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
