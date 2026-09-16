package com.sendmefile77.gamebroth.ai

import java.io.File

/**
 * Memory-safety policy for the in-process Android image generator.
 *
 * The embedded renderer is deliberately a Q4 GGUF path. Large Q8/F16 checkpoints and
 * safetensors can fit on disk but create enough model/backend/workspace pressure to make Android
 * kill other applications. Keep that failure mode out of normal gameplay instead of relying on
 * users to remember which checkpoint is safe.
 */
internal object MobileImageModelPolicy {
    const val RECOMMENDED_MODEL = "WAI-illustrious-SDXL-v170-Q4_K_M.gguf"

    // The recommended WAI Q4_K_M file is comfortably below this. The ceiling also prevents a
    // Q8/F16 checkpoint renamed with a Q4-looking name from entering the mobile fast path.
    const val MAX_Q4_FILE_BYTES: Long = 2_200_000_000L

    private val q4Markers = listOf(
        "q4_k_m",
        "q4_k_s",
        "q4_0",
        "q4_1",
    )

    fun validationError(name: String, sizeBytes: Long?): String? {
        val normalized = name.lowercase()
        if (!normalized.endsWith(".gguf")) {
            return "Для встроенного генератора нужна Q4 GGUF-модель. Выберите $RECOMMENDED_MODEL"
        }
        if (q4Markers.none(normalized::contains)) {
            return "Эта GGUF не помечена как Q4. Q8/F16 в мобильном режиме заблокированы; выберите $RECOMMENDED_MODEL"
        }
        if (sizeBytes != null && sizeBytes > MAX_Q4_FILE_BYTES) {
            return "Файл слишком большой для мобильного Q4-профиля (${formatSize(sizeBytes)}). Выберите $RECOMMENDED_MODEL"
        }
        return null
    }

    fun validationError(file: File): String? = validationError(file.name, file.takeIf { it.isFile }?.length())

    fun readyStatus(file: File): String = "Q4 готова: ${file.name} · ${formatSize(file.length())}"

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "размер неизвестен"
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.2f ГБ", gib)
    }
}
