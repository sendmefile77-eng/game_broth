package com.sendmefile77.gamebroth

import android.app.Application
import com.sendmefile77.gamebroth.ai.ImageBackendConfig
import com.sendmefile77.gamebroth.ai.TextBackendConfig

/** Initializes persisted AI backend choices before any screen or generation request can use them. */
class GameBrothApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ImageBackendConfig.initialize(this)
        TextBackendConfig.initialize(this)
    }
}
