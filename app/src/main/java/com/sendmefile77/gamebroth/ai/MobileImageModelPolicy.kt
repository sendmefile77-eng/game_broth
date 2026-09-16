package com.sendmefile77.gamebroth.ai

import java.io.File

internal enum class MobileImageModelComponent(val displayName: String) {
    DIFFUSION("Q4 diffusion"),
    CLIP_L("CLIP-L"),
    CLIP_G("CLIP-G"),
    VAE("VAE"),
}

/**
 * Memory-safety and completeness policy for the in-process Android image generator.
 *
 * The recommended WAI Q4_K_M GGUF is a standalone SDXL diffusion/UNet file, not a complete
 * checkpoint. stable-diffusion.cpp therefore needs the separate CLIP-L, CLIP-G and VAE files as
 * well. The importer stores those three components next to the Q4 file using canonical names so
 * native code can load the pack deterministically.
 */
internal object MobileImageModelPolicy {
    const val RECOMMENDED_MODEL = "WAI-illustrious-SDXL-v170-Q4_K_M.gguf"
    const val CLIP_L_FILE = "clip_l.safetensors"
    const val CLIP_G_FILE = "clip_g.safetensors"
    const val VAE_FILE = "vae.safetensors"

    const val MAX_Q4_FILE_BYTES: Long = 2_200_000_000L
    private const val MAX_CLIP_L_BYTES: Long = 500_000_000L
    private const val MAX_CLIP_G_BYTES: Long = 1_700_000_000L
    private const val MAX_VAE_BYTES: Long = 600_000_000L

    // Used only for generic Diffusers names such as model.safetensors.
    private const val GENERIC_VAE_MAX_BYTES: Long = 210_000_000L
    private const val GENERIC_CLIP_L_MAX_BYTES: Long = 500_000_000L

    private val q4Markers = listOf("q4_k_m", "q4_k_s", "q4_0", "q4_1")

    /** Backwards-compatible name/size check used by existing tests: validates only the Q4 file. */
    fun validationError(name: String, sizeBytes: Long?): String? = diffusionValidationError(name, sizeBytes)

    fun diffusionValidationError(name: String, sizeBytes: Long?): String? {
        val normalized = name.lowercase()
        if (!normalized.endsWith(".gguf")) {
            return "Для встроенного генератора нужна Q4 GGUF diffusion-модель. Выберите $RECOMMENDED_MODEL"
        }
        if (q4Markers.none(normalized::contains)) {
            return "Эта GGUF не помечена как Q4. Q8/F16 в мобильном режиме заблокированы; выберите $RECOMMENDED_MODEL"
        }
        if (sizeBytes != null && sizeBytes > MAX_Q4_FILE_BYTES) {
            return "Файл слишком большой для мобильного Q4-профиля (${formatSize(sizeBytes)}). Выберите $RECOMMENDED_MODEL"
        }
        return null
    }

    fun diffusionValidationError(file: File): String? =
        diffusionValidationError(file.name, file.takeIf { it.isFile }?.length())

    /**
     * Detects which part of the split SDXL pack the user selected. Explicit names win; exact-v170
     * Diffusers files have generic names, so their known size ranges are used as a fallback.
     */
    fun detectComponent(name: String, sizeBytes: Long?): MobileImageModelComponent? {
        val normalized = name.lowercase()
        if (normalized.endsWith(".gguf") && q4Markers.any(normalized::contains)) {
            return MobileImageModelComponent.DIFFUSION
        }
        if (!(normalized.endsWith(".safetensors") || normalized.endsWith(".gguf"))) return null

        when {
            normalized.contains("clip_l") || normalized.contains("clip-l") -> return MobileImageModelComponent.CLIP_L
            normalized.contains("clip_g") || normalized.contains("clip-g") -> return MobileImageModelComponent.CLIP_G
            normalized.contains("vae") -> return MobileImageModelComponent.VAE
        }

        if (!normalized.endsWith(".safetensors") || sizeBytes == null) return null
        return when {
            sizeBytes in 50_000_000L..GENERIC_VAE_MAX_BYTES -> MobileImageModelComponent.VAE
            sizeBytes in (GENERIC_VAE_MAX_BYTES + 1)..GENERIC_CLIP_L_MAX_BYTES -> MobileImageModelComponent.CLIP_L
            sizeBytes in (GENERIC_CLIP_L_MAX_BYTES + 1)..MAX_CLIP_G_BYTES -> MobileImageModelComponent.CLIP_G
            else -> null
        }
    }

    fun componentValidationError(component: MobileImageModelComponent, name: String, sizeBytes: Long?): String? {
        if (component == MobileImageModelComponent.DIFFUSION) return diffusionValidationError(name, sizeBytes)
        val normalized = name.lowercase()
        if (!normalized.endsWith(".safetensors")) {
            return "${component.displayName}: для этого мобильного комплекта нужен файл .safetensors"
        }
        if (sizeBytes == null || sizeBytes <= 0L) return null
        val max = when (component) {
            MobileImageModelComponent.CLIP_L -> MAX_CLIP_L_BYTES
            MobileImageModelComponent.CLIP_G -> MAX_CLIP_G_BYTES
            MobileImageModelComponent.VAE -> MAX_VAE_BYTES
            MobileImageModelComponent.DIFFUSION -> MAX_Q4_FILE_BYTES
        }
        if (sizeBytes > max) {
            return "${component.displayName}: файл выглядит слишком большим (${formatSize(sizeBytes)})"
        }
        return null
    }

    fun canonicalFile(component: MobileImageModelComponent, directory: File): File = when (component) {
        MobileImageModelComponent.CLIP_L -> File(directory, CLIP_L_FILE)
        MobileImageModelComponent.CLIP_G -> File(directory, CLIP_G_FILE)
        MobileImageModelComponent.VAE -> File(directory, VAE_FILE)
        MobileImageModelComponent.DIFFUSION -> error("diffusion file keeps its original Q4 filename")
    }

    /** Full-pack validation used before native loading. */
    fun validationError(file: File): String? {
        diffusionValidationError(file)?.let { return it }
        val directory = file.parentFile ?: return "Не удалось определить папку Q4-комплекта"
        val missing = mutableListOf<String>()
        if (!File(directory, CLIP_L_FILE).isFile) missing += "CLIP-L"
        if (!File(directory, CLIP_G_FILE).isFile) missing += "CLIP-G"
        if (!File(directory, VAE_FILE).isFile) missing += "VAE"
        if (missing.isNotEmpty()) {
            return "Q4 уже выбрана. Добавьте ${missing.joinToString(", ")} через ту же кнопку выбора модели."
        }
        componentValidationError(MobileImageModelComponent.CLIP_L, CLIP_L_FILE, File(directory, CLIP_L_FILE).length())?.let { return it }
        componentValidationError(MobileImageModelComponent.CLIP_G, CLIP_G_FILE, File(directory, CLIP_G_FILE).length())?.let { return it }
        componentValidationError(MobileImageModelComponent.VAE, VAE_FILE, File(directory, VAE_FILE).length())?.let { return it }
        return null
    }

    fun readyStatus(file: File): String = if (validationError(file) == null) {
        "Q4-комплект готов: ${file.name} · CLIP-L · CLIP-G · VAE"
    } else {
        validationError(file) ?: "Q4-комплект не готов"
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "размер неизвестен"
        val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.2f ГБ", gib)
    }
}
