package com.sendmefile77.gamebroth.ai

import android.content.Context

enum class ImageBackendMode {
    LOCAL_DREAM,
    EMBEDDED,
}

object ImageBackendConfig {
    private const val PREFS = "image_backend_settings"
    private const val KEY_MODE = "mode"
    private const val KEY_MODEL_URI = "model_uri"

    @Volatile
    private var initialized = false

    @Volatile
    var mode: ImageBackendMode = ImageBackendMode.LOCAL_DREAM
        private set

    @Volatile
    var modelUri: String? = null
        private set

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mode = runCatching {
                ImageBackendMode.valueOf(prefs.getString(KEY_MODE, ImageBackendMode.LOCAL_DREAM.name).orEmpty())
            }.getOrDefault(ImageBackendMode.LOCAL_DREAM)
            modelUri = prefs.getString(KEY_MODEL_URI, null)?.takeIf { it.isNotBlank() }
            initialized = true
        }
    }

    fun setMode(context: Context, value: ImageBackendMode) {
        initialize(context)
        mode = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, value.name)
            .apply()
    }

    fun setModelUri(context: Context, value: String?) {
        initialize(context)
        modelUri = value?.takeIf { it.isNotBlank() }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_URI, modelUri)
            .apply()
    }

    fun label(): String = when (mode) {
        ImageBackendMode.LOCAL_DREAM -> "Local Dream"
        ImageBackendMode.EMBEDDED -> "Встроенный stable-diffusion.cpp"
    }
}
