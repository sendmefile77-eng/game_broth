package com.sendmefile77.gamebroth.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MobileImageModelPolicyTest {
    @Test
    fun acceptsRecommendedQ4KModel() {
        assertNull(
            MobileImageModelPolicy.validationError(
                "WAI-illustrious-SDXL-v170-Q4_K_M.gguf",
                1_750_000_000L,
            ),
        )
    }

    @Test
    fun rejectsQ8Model() {
        assertNotNull(
            MobileImageModelPolicy.validationError(
                "WAI-illustrious-SDXL-v170-Q8_0.gguf",
                2_740_000_000L,
            ),
        )
    }

    @Test
    fun rejectsSafetensorsCheckpoint() {
        assertNotNull(
            MobileImageModelPolicy.validationError(
                "waiIllustriousSDXL_v170.safetensors",
                6_900_000_000L,
            ),
        )
    }

    @Test
    fun rejectsSuspiciouslyLargeFileEvenWithQ4Name() {
        assertNotNull(
            MobileImageModelPolicy.validationError(
                "renamed-Q4_K_M.gguf",
                MobileImageModelPolicy.MAX_Q4_FILE_BYTES + 1L,
            ),
        )
    }
}
