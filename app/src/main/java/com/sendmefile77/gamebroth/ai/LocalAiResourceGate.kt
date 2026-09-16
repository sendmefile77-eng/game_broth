package com.sendmefile77.gamebroth.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One heavy local AI job at a time. Today it serializes Tellama and image generation; when the text
 * model moves in-process, the same gate guarantees that text is unloaded before diffusion starts.
 */
object LocalAiResourceGate {
    private val mutex = Mutex()

    suspend fun <T> withSlot(block: suspend () -> T): T = mutex.withLock { block() }
}
