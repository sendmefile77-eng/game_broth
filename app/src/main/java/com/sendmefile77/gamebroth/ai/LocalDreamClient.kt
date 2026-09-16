package com.sendmefile77.gamebroth.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.sendmefile77.gamebroth.aiimage.ImageAiStatus
import com.sendmefile77.gamebroth.aiimage.ImageGenerationRequest
import com.sendmefile77.gamebroth.aiimage.ImageGenerationResult
import com.sendmefile77.gamebroth.aiimage.ImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream

class LocalDreamClient(
    private val baseUrl: String = "http://127.0.0.1:8081",
) : ImageGenerator {
    private val mutex = Mutex()

    override suspend fun status(force: Boolean): ImageAiStatus = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open("/health", "GET", 2_500)
            try {
                val code = connection.responseCode
                if (code in 200..299) ImageAiStatus(true)
                else ImageAiStatus(false, "Local Dream /health: HTTP $code")
            } finally {
                connection.disconnect()
            }
        }.getOrElse { ImageAiStatus(false, it.message ?: "Local Dream недоступна") }
    }

    override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("prompt", request.prompt)
                .put("negative_prompt", request.negativePrompt)
                .put("steps", request.steps.coerceIn(1, 50))
                .put("cfg", request.cfgScale.coerceIn(1.0, 30.0))
                .put("seed", request.seed)
                .put("scheduler", "dpmpp_2m")
                .put("width", request.width)
                .put("height", request.height)
                .put("aspect_ratio", "${request.width}:${request.height}")
                .put("show_diffusion_process", false)
                .put("output_format", "png")
            request.referenceImageBytes?.takeIf { it.isNotEmpty() }?.let { reference ->
                payload.put("image", Base64.encodeToString(reference, Base64.NO_WRAP))
                val denoise = maxOf(request.referenceStrength, 0.82).coerceIn(0.05, 1.0)
                payload.put("denoise_strength", denoise)
            }

            val connection = open("/generate", "POST", 135_000).apply {
                doOutput = true
                setRequestProperty("Accept", "text/event-stream, application/json")
            }
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) error("Local Dream /generate: HTTP $code")
                var eventName = ""
                val data = StringBuilder()
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) {
                            decodeComplete(eventName, data.toString())?.let { return@withContext it }
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
                decodeComplete(eventName, data.toString())
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun decodeComplete(eventName: String, data: String): ImageGenerationResult? {
        if (data.isBlank()) return null
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
        val type = json.optString("type", eventName)
        if (type.equals("error", true) || eventName.equals("error", true)) {
            error(json.optString("message").ifBlank { "Local Dream generation error" })
        }
        if (!type.equals("complete", true) && !eventName.equals("complete", true)) return null
        val encoded = json.optString("image")
        if (encoded.isBlank()) return null
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val width = json.optInt("width")
        val height = json.optInt("height")
        val channels = json.optInt("channels", 3)
        val format = json.optString("format", "png").lowercase()
        val normalized = when (format) {
            "png", "jpeg", "jpg" -> decoded
            else -> rawToPng(decoded, width, height, channels)
        }
        return ImageGenerationResult(
            bytes = normalized,
            seed = json.optLong("seed").takeIf { json.has("seed") },
            width = width,
            height = height,
            generationTimeMs = json.optLong("generation_time_ms").takeIf { json.has("generation_time_ms") },
        )
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
        } finally { bitmap.recycle() }
    }

    private fun open(path: String, method: String, timeout: Int): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeout, 5_000)
            readTimeout = timeout
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "GameBroth/0.4.4")
        }
}
