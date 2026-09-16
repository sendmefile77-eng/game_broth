package com.sendmefile77.gamebroth.ai

import com.sendmefile77.gamebroth.aitext.TextAiStatus
import com.sendmefile77.gamebroth.aitext.TextGenerationRequest
import com.sendmefile77.gamebroth.aitext.TextGenerationResult
import com.sendmefile77.gamebroth.aitext.TextNarrator

/**
 * Stable routing facade for text generation.
 *
 * Game/UI code should depend on this class (or TextNarrator), not on a concrete runtime. That lets
 * us keep the existing Tellama/Ollama-compatible path while adding an in-process llama.cpp path.
 */
class TextBackendClient(
    private val tellama: TextNarrator,
    private val embedded: TextNarrator = EmbeddedTextClient(),
) : TextNarrator {
    override suspend fun status(force: Boolean): TextAiStatus = when (TextBackendConfig.mode) {
        TextBackendMode.TELLAMA -> tellama.status(force)
        TextBackendMode.EMBEDDED -> embedded.status(force)
    }

    override suspend fun generate(request: TextGenerationRequest): TextGenerationResult? = when (TextBackendConfig.mode) {
        TextBackendMode.TELLAMA -> tellama.generate(request)
        TextBackendMode.EMBEDDED -> embedded.generate(request)
    }
}
