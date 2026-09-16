package com.sendmefile77.gamebroth.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.sendmefile77.gamebroth.aiimage.ImageAiStatus
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.ImageGenerationResult
import com.sendmefile77.gamebroth.aiimage.ImageGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Image-generator entry point used by the game.
 *
 * Local Dream is now completely owned by this APK: EmbeddedLocalDreamRuntime starts the packaged
 * native core on a private loopback port and this client talks to that process. No separately
 * installed Local Dream application is used or required.
 */
class LocalDreamClient : ImageGenerator {
    private val mutex = Mutex()
    private val runtime = EmbeddedLocalDreamRuntime()

    override suspend fun status(force: Boolean): ImageAiStatus = withContext(Dispatchers.IO) {
        val model = ImageBackendConfig.extractedModelDir()
            ?: return@withContext ImageAiStatus(false, ImageBackendConfig.modelImportStatus)
        ImageBackendConfig.modelValidationError(model)?.let {
            return@withContext ImageAiStatus(false, it)
        }
        val problem = runtime.ensureRunning()
        if (problem == null) {
            ImageAiStatus(true, "${model.name}; ${runtime.runtimeInfo()}")
        } else {
            ImageAiStatus(false, problem)
        }
    }

    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult? =
        LocalAiResourceGate.withSlot { generateInternal(request) }

    /**
     * The native process stays warm across a portrait queue and after it. The Android process owns
     * its lifetime, so later portraits do not pay model-startup cost again unless the model changed.
     */
    suspend fun <T> withGenerationSession(
        block: suspend (generate: suspend (ImageGenerationRequest) -> ImageGenerationResult?) -> T,
    ): T = LocalAiResourceGate.withSlot {
        block { request -> generateInternal(request) }
    }

    private suspend fun generateInternal(request: ImageGenerationRequest): ImageGenerationResult? = mutex.withLock {
        try {
            ImageBackendConfig.clearRuntimeError()
            ImageGenerationProgressStore.loading(
                width = request.width,
                height = request.height,
                seed = request.seed,
            )
            runtime.ensureRunning()?.let { error(it) }
            ImageGenerationProgressStore.preparing(request.width, request.height, request.seed)
            generateOverLoopback(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val nativeTail = runtime.diagnosticTail()
            val detail = buildString {
                append(error.message ?: error::class.java.simpleName)
                if (nativeTail.isNotBlank() && nativeTail !in this) {
                    append(" · Local Dream: ").append(nativeTail.takeLast(1_200))
                }
            }.take(2_000)
            ImageBackendConfig.reportRuntimeError(detail)
            ImageGenerationProgressStore.failed("Не удалось создать изображение", detail)
            throw error
        }
    }

    private suspend fun generateOverLoopback(request: ImageGenerationRequest): ImageGenerationResult? =
        withContext(Dispatchers.IO) {
            val tuning = tuningFor(request)
            val payload = JSONObject()
                .put("prompt", request.prompt)
                .put("negative_prompt", request.negativePrompt)
                .put("steps", tuning.steps)
                .put("cfg", tuning.cfg)
                .put("seed", request.seed)
                .put("scheduler", "dpmpp_2m")
                .put("width", request.width)
                .put("height", request.height)
                .put("aspect_ratio", "${request.width}:${request.height}")
                .put("show_diffusion_process", false)
                .put("output_format", "png")

            request.referenceImageBytes?.takeIf { it.isNotEmpty() }?.let { reference ->
                payload.put("image", Base64.encodeToString(reference, Base64.NO_WRAP))
                payload.put("denoise_strength", request.referenceStrength.coerceIn(0.05, 0.75))
            }

            val connection = open("/generate", "POST", GENERATION_TIMEOUT_MS).apply {
                doOutput = true
                setRequestProperty("Accept", "text/event-stream, application/json")
            }
            try {
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val errorBody = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
                    error("Local Dream /generate: HTTP $code${errorBody?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
                }

                var eventName = ""
                val data = StringBuilder()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) {
                            processEvent(eventName, data.toString(), request)?.let { return@withContext it }
                            eventName = ""
                            data.setLength(0)
                            continue
                        }
                        when {
                            line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substringAfter(':').trimStart())
                            }
                        }
                    }
                }
                processEvent(eventName, data.toString(), request)
            } finally {
                connection.disconnect()
            }
        }

    private fun processEvent(
        eventName: String,
        data: String,
        request: ImageGenerationRequest,
    ): ImageGenerationResult? {
        if (data.isBlank()) return null
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
        val type = json.optString("type", eventName).lowercase()

        if (type == "progress" || eventName.equals("progress", true)) {
            val step = json.optInt("step", 0)
            val total = json.optInt("total_steps", 0)
            if (total > 0) {
                ImageGenerationProgressStore.diffusion(
                    step = step,
                    totalSteps = total,
                    width = request.width,
                    height = request.height,
                    seed = request.seed,
                )
            }
            return null
        }

        if (type == "error" || eventName.equals("error", true)) {
            error(json.optString("message").ifBlank { "Local Dream generation error" })
        }
        if (type != "complete" && !eventName.equals("complete", true)) return null

        val encoded = json.optString("image")
        require(encoded.isNotBlank()) { "Local Dream завершил генерацию без изображения" }
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val width = json.optInt("width", request.width)
        val height = json.optInt("height", request.height)
        val channels = json.optInt("channels", 3)
        val format = json.optString("format", "png").lowercase()

        ImageGenerationProgressStore.rgbReceived(width, height, request.seed)
        val normalized = when (format) {
            "png" -> decoded.also {
                require(PngPayload.isPng(it)) { "Local Dream вернул повреждённый PNG" }
            }
            "jpeg", "jpg" -> {
                ImageGenerationProgressStore.encoding(width, height, request.seed)
                compressedToPng(decoded)
            }
            else -> {
                ImageGenerationProgressStore.encoding(width, height, request.seed)
                rawToPng(decoded, width, height, channels)
            }
        }
        return ImageGenerationResult(
            bytes = normalized,
            seed = json.optLong("seed").takeIf { json.has("seed") } ?: request.seed,
            width = width,
            height = height,
            generationTimeMs = json.optLong("generation_time_ms").takeIf { json.has("generation_time_ms") },
        )
    }

    private fun tuningFor(request: ImageGenerationRequest): LocalDreamTuning {
        val key = request.cacheKey.lowercase()
        return when {
            key.startsWith("recruit/") -> LocalDreamTuning(steps = 20, cfg = 7.0)
            "/portrait/" in key -> LocalDreamTuning(steps = 20, cfg = 7.0)
            "/event/" in key -> LocalDreamTuning(steps = 24, cfg = 7.0)
            "/scene/" in key -> LocalDreamTuning(steps = 22, cfg = 6.8)
            else -> LocalDreamTuning(
                steps = request.steps.coerceIn(1, 50),
                cfg = request.cfgScale.coerceIn(1.0, 30.0),
            )
        }
    }

    private fun rawToPng(raw: ByteArray, width: Int, height: Int, channels: Int): ByteArray {
        require(width > 0 && height > 0 && channels in 3..4) { "Local Dream returned invalid raw image dimensions" }
        val pixelCount = width.toLong() * height.toLong()
        require(pixelCount in 1..16_000_000L) { "Local Dream image is too large" }
        val expected = pixelCount * channels
        require(raw.size.toLong() >= expected) { "Local Dream raw image is truncated" }
        val pixels = IntArray(pixelCount.toInt())
        var source = 0
        for (i in pixels.indices) {
            val r = raw[source].toInt() and 0xff
            val g = raw[source + 1].toInt() and 0xff
            val b = raw[source + 2].toInt() and 0xff
            val a = if (channels == 4) raw[source + 3].toInt() and 0xff else 0xff
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            source += channels
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG conversion failed" }
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun compressedToPng(compressed: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.size)
            ?: error("Local Dream вернул изображение, которое Android не может декодировать")
        return try {
            ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG conversion failed" }
                out.toByteArray().also { require(PngPayload.isPng(it)) { "PNG conversion produced invalid data" } }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun open(path: String, method: String, timeout: Int): HttpURLConnection =
        (URL(runtime.endpoint(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeout, 5_000)
            readTimeout = timeout
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "GameBroth/1.0.1 embedded-localdream")
        }

    private companion object {
        const val GENERATION_TIMEOUT_MS = 300_000
    }
}

private data class LocalDreamTuning(
    val steps: Int,
    val cfg: Double,
)
