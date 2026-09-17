package com.sendmefile77.gamebroth.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ImageGenerationStage {
    IDLE,
    LOADING_MODEL,
    PREPARING,
    DIFFUSION,
    VAE_DECODE,
    RGB_TRANSFER,
    ENCODING_PNG,
    SAVING_FILE,
    ATTACHING_ENTITY,
    COMPLETE,
    FAILED,
}

data class ImageGenerationProgress(
    val stage: ImageGenerationStage = ImageGenerationStage.IDLE,
    val message: String = "",
    val technicalDetail: String? = null,
    val step: Int = 0,
    val totalSteps: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val seed: Long? = null,
    val subjectName: String? = null,
    val itemIndex: Int = 0,
    val itemTotal: Int = 0,
) {
    val running: Boolean
        get() = stage in setOf(
            ImageGenerationStage.LOADING_MODEL,
            ImageGenerationStage.PREPARING,
            ImageGenerationStage.DIFFUSION,
            ImageGenerationStage.VAE_DECODE,
            ImageGenerationStage.RGB_TRANSFER,
            ImageGenerationStage.ENCODING_PNG,
            ImageGenerationStage.SAVING_FILE,
            ImageGenerationStage.ATTACHING_ENTITY,
        )

    /** A percentage is exposed only for an engine phase with a real denominator. */
    val fraction: Float?
        get() = if (
            stage in setOf(
                ImageGenerationStage.LOADING_MODEL,
                ImageGenerationStage.DIFFUSION,
                ImageGenerationStage.VAE_DECODE,
            ) && totalSteps > 0
        ) {
            (step.coerceIn(0, totalSteps).toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    val counterText: String?
        get() = if (totalSteps <= 0) null else when (stage) {
            ImageGenerationStage.LOADING_MODEL -> "Тензор ${step.coerceIn(0, totalSteps)} из $totalSteps"
            ImageGenerationStage.DIFFUSION -> "Этап ${step.coerceIn(0, totalSteps)} из $totalSteps"
            ImageGenerationStage.VAE_DECODE -> "Блок ${step.coerceIn(0, totalSteps)} из $totalSteps"
            else -> null
        }

    val subjectText: String?
        get() = subjectName?.let { name ->
            if (itemIndex > 0 && itemTotal > 0) "$name · портрет $itemIndex из $itemTotal" else name
        }
}

/**
 * UI-observable truth about the complete portrait pipeline. COMPLETE is deliberately owned by the
 * caller that saved and attached the file; producing PNG bytes alone is not completion.
 */
object ImageGenerationProgressStore {
    private val _state = MutableStateFlow(ImageGenerationProgress())
    val state: StateFlow<ImageGenerationProgress> = _state.asStateFlow()

    fun begin(
        subjectName: String,
        itemIndex: Int = 1,
        itemTotal: Int = 1,
        width: Int = 0,
        height: Int = 0,
        seed: Long? = null,
    ) {
        _state.value = ImageGenerationProgress(
            stage = ImageGenerationStage.PREPARING,
            message = "Запускаем генератор…",
            width = width,
            height = height,
            seed = seed,
            subjectName = subjectName,
            itemIndex = itemIndex,
            itemTotal = itemTotal,
        )
    }

    fun loading(step: Int = 0, total: Int = 0, width: Int? = null, height: Int? = null, seed: Long? = null) {
        update(
            stage = ImageGenerationStage.LOADING_MODEL,
            message = "Загрузка модели…",
            step = step,
            total = total,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun preparing(width: Int, height: Int, seed: Long) {
        update(
            stage = ImageGenerationStage.PREPARING,
            message = "Подготовка модели и промпта…",
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun diffusion(step: Int, totalSteps: Int, width: Int, height: Int, seed: Long) {
        val safeTotal = totalSteps.coerceAtLeast(1)
        val safeStep = step.coerceIn(0, safeTotal)
        update(
            stage = ImageGenerationStage.DIFFUSION,
            message = "Генерация на NPU — этап $safeStep из $safeTotal",
            step = safeStep,
            total = safeTotal,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun decoding(step: Int = 0, total: Int = 0, width: Int? = null, height: Int? = null, seed: Long? = null) {
        update(
            stage = ImageGenerationStage.VAE_DECODE,
            message = "Декодирование изображения…",
            step = step,
            total = total,
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun rgbReceived(width: Int, height: Int, seed: Long) {
        update(
            stage = ImageGenerationStage.RGB_TRANSFER,
            message = "Изображение получено из движка…",
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun encoding(width: Int, height: Int, seed: Long) {
        update(
            stage = ImageGenerationStage.ENCODING_PNG,
            message = "Упаковка изображения в PNG…",
            width = width,
            height = height,
            seed = seed,
        )
    }

    fun saving() {
        update(stage = ImageGenerationStage.SAVING_FILE, message = "Сохранение портрета…")
    }

    fun attaching() {
        update(stage = ImageGenerationStage.ATTACHING_ENTITY, message = "Привязка портрета к персонажу…")
    }

    fun complete() {
        update(stage = ImageGenerationStage.COMPLETE, message = "Портрет готов", keepCounter = false)
    }

    fun failed(userMessage: String, technicalDetail: String? = null) {
        update(
            stage = ImageGenerationStage.FAILED,
            message = userMessage,
            technicalDetail = technicalDetail,
            keepCounter = false,
        )
    }

    fun reset() {
        _state.value = ImageGenerationProgress()
    }

    private fun update(
        stage: ImageGenerationStage,
        message: String,
        technicalDetail: String? = null,
        step: Int = 0,
        total: Int = 0,
        width: Int? = null,
        height: Int? = null,
        seed: Long? = null,
        keepCounter: Boolean = true,
    ) {
        val previous = _state.value
        val safeTotal = total.coerceAtLeast(0)
        val safeStep = if (safeTotal > 0) step.coerceIn(0, safeTotal) else step.coerceAtLeast(0)
        _state.value = previous.copy(
            stage = stage,
            message = message,
            technicalDetail = technicalDetail,
            step = if (keepCounter) safeStep else 0,
            totalSteps = if (keepCounter) safeTotal else 0,
            width = width ?: previous.width,
            height = height ?: previous.height,
            seed = seed ?: previous.seed,
        )
    }
}
