package com.sendmefile77.gamebroth.ai

import com.sendmefile77.gamebroth.aitext.TextAiStatus
import com.sendmefile77.gamebroth.aitext.TextGenerationRequest
import com.sendmefile77.gamebroth.aitext.TextGenerationResult
import com.sendmefile77.gamebroth.aitext.TextNarrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Existing Tellama/Ollama-compatible client plus the text-backend routing point used by current UI.
 * TELLAMA stays the default. When settings switch to EMBEDDED, requests are delegated to llama.cpp
 * through EmbeddedTextClient without changing game/simulation code.
 */
class TellamaClient(
    private val apiKeyProvider: () -> String?,
    private val baseUrl: String = "http://127.0.0.1:11434",
) : TextNarrator {
    private val mutex = Mutex()
    private val embedded = EmbeddedTextClient()
    @Volatile private var cachedModel: String? = null

    override suspend fun status(force: Boolean): TextAiStatus = when (TextBackendConfig.mode) {
        TextBackendMode.TELLAMA -> tellamaStatus(force)
        TextBackendMode.EMBEDDED -> embedded.status(force)
    }

    override suspend fun generate(request: TextGenerationRequest): TextGenerationResult? = when (TextBackendConfig.mode) {
        TextBackendMode.TELLAMA -> generateWithTellama(request)
        TextBackendMode.EMBEDDED -> embedded.generate(request)
    }

    private suspend fun tellamaStatus(force: Boolean): TextAiStatus = withContext(Dispatchers.IO) {
        val key = apiKeyProvider()?.trim().orEmpty()
        if (!force) cachedModel?.let { return@withContext TextAiStatus(true, it) }
        runCatching {
            val connection = open("/api/tags", "GET", 3_000, key)
            try {
                val code = connection.responseCode
                val body = streamText(connection, code)
                if (code !in 200..299) error("Tellama /api/tags: HTTP $code")
                val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
                val model = (0 until models.length()).asSequence().mapNotNull { index ->
                    models.optJSONObject(index)?.let { item ->
                        item.optString("model").takeIf(String::isNotBlank)
                            ?: item.optString("name").takeIf(String::isNotBlank)
                    }
                }.firstOrNull()
                if (model == null) TextAiStatus(false, detail = "Локальный текстовый сервер запущен, но модель не выбрана")
                else TextAiStatus(true, model).also { cachedModel = model }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { TextAiStatus(false, detail = it.message ?: "Локальная текстовая модель недоступна") }
    }

    private suspend fun generateWithTellama(request: TextGenerationRequest): TextGenerationResult? = LocalAiResourceGate.withSlot {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val key = apiKeyProvider()?.trim().orEmpty()
                val model = tellamaStatus(force = false).model ?: return@withContext null
                val eventDigest = request.recentEvents.take(12).joinToString("\n") { "D${it.day} ${it.type}: ${it.summary}" }
                val userPrompt = buildString {
                    append("STATE\n").append(request.stateDigest)
                    if (eventDigest.isNotBlank()) append("\nRECENT EVENTS\n").append(eventDigest)
                    append("\nCOMPLETED DAY FACTS\n").append(request.playerAction)
                    append("\nWrite only from these facts. Never invent or change numeric state; the simulation engine owns all mechanics.")
                }
                val payload = JSONObject()
                    .put("model", model)
                    .put("stream", true)
                    // Release external model RAM after every narration so diffusion can take the same phone memory.
                    .put("keep_alive", 0)
                    .put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", request.systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt)))
                    .put("options", JSONObject()
                        .put("temperature", request.temperature.coerceIn(0.0, 2.0))
                        .put("num_predict", request.maxTokens.coerceIn(128, 2400))
                        .put("top_p", 0.88))

                val started = System.currentTimeMillis()
                val connection = open("/api/chat", "POST", 75_000, key).apply {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                try {
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
                    val code = connection.responseCode
                    if (code !in 200..299) error("Tellama /api/chat: HTTP $code ${streamText(connection, code).take(160)}")
                    val content = StringBuilder()
                    connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                        for (line in lines) {
                            if (line.isBlank()) continue
                            val json = JSONObject(line)
                            json.optJSONObject("message")?.optString("content")?.let(content::append)
                            if (json.optBoolean("done", false)) break
                        }
                    }
                    content.toString().trim().takeIf(String::isNotBlank)?.let {
                        TextGenerationResult(it, model, System.currentTimeMillis() - started)
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun open(path: String, method: String, timeout: Int, key: String): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(timeout, 3_000)
            readTimeout = timeout
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
        }

    private fun streamText(connection: HttpURLConnection, code: Int): String =
        (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}
