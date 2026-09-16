package com.sendmefile77.gamebroth.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ImageGenerationStage {
    IDLE,
    LOADING_MODEL,
    PREPARING,
    GENERATING,
    ENCODING,
    UNLOADING_MODEL,
    COMPLETE,
    FAILED,
}

data class ImageGenerationProgress(
    val stage: ImageGenerationStage = ImageGenerationStage.IDLE,
    val message: String = "",
    val step: Int = 0,
    val totalSteps: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val seed: Long? = null,
) {
    val running: Boolean
        get() = stage in setOf(
            ImageGenerationStage.LOADING_MODEL,
            ImageGenerationStage.PREPARING,
            ImageGenerationStage.GENERATING,
            ImageGenerationStage.ENCODING,
            ImageGenerationStage.UNLOADING_MODEL,
        )

    val fraction: Float?
        get() = if (stage == ImageGenerationStage.GENERATING && totalSteps > 0) {
            (step.coerceIn(0, totalSteps).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
        } else null
}

/**
 * Shared, UI-observable state for the image pipeline.
 * Embedded stable-diffusion.cpp updates exact denoising steps from its native callback.
 */
object ImageGenerationProgressStore {
    private val _state = MutableStateFlow(ImageGenerationProgress())
    val state: StateFlow<ImageGenerationProgress> = _state.asStateFlow()

    fun loading(width: Int, height: Int, seed: Long) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.LOADING_MODEL,
            message = "Загружаем модель в память…",
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun preparing(width: Int, height: Int, seed: Long, totalSteps: Int) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.PREPARING,
            message = "Подготавливаем промпт и латенты…",
            totalSteps = totalSteps,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun generating(step: Int, totalSteps: Int, width: Int, height: Int, seed: Long) {
        val safeTotal = totalSteps.coerceAtLeast(1)
        val safeStep = step.coerceIn(0, safeTotal)
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.GENERATING,
            message = "Генерация: шаг $safeStep из $safeTotal",
            step = safeStep,
            totalSteps = safeTotal,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun encoding(width: Int, height: Int, seed: Long, totalSteps: Int) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.ENCODING,
            message = "Упаковываем изображение в PNG…",
            step = totalSteps,
            totalSteps = totalSteps,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun unloading(width: Int, height: Int, seed: Long, totalSteps: Int) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.UNLOADING_MODEL,
            message = "Освобождаем память модели…",
            step = totalSteps,
            totalSteps = totalSteps,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun complete(width: Int, height: Int, seed: Long, totalSteps: Int) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.COMPLETE,
            message = "Изображение готово",
            step = totalSteps,
            totalSteps = totalSteps,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun failed(message: String) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.FAILED,
            message = message,
        )
    }

    fun reset() {
        _state.value = ImageGenerationProgress()
    }
}
