package com.sendmefile77.gamebroth.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MobileImageModelPolicyTest {
    @Test
    fun acceptsRecommendedQ4KDiffusionFile() {
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
    fun rejectsSafetensorsCheckpointAsDiffusionFile() {
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

    @Test
    fun recognizesExplicitComponentNames() {
        assertEquals(
            MobileImageModelComponent.CLIP_L,
            MobileImageModelPolicy.detectComponent("clip_l.safetensors", 247_000_000L),
        )
        assertEquals(
            MobileImageModelComponent.CLIP_G,
            MobileImageModelPolicy.detectComponent("clip_g.safetensors", 1_390_000_000L),
        )
        assertEquals(
            MobileImageModelComponent.VAE,
            MobileImageModelPolicy.detectComponent("sdxl_vae.safetensors", 167_000_000L),
        )
    }

    @Test
    fun recognizesExactV170DiffusersGenericFilesBySize() {
        assertEquals(
            MobileImageModelComponent.VAE,
            MobileImageModelPolicy.detectComponent("diffusion_pytorch_model.safetensors", 167_000_000L),
        )
        assertEquals(
            MobileImageModelComponent.CLIP_L,
            MobileImageModelPolicy.detectComponent("model.safetensors", 246_000_000L),
        )
        assertEquals(
            MobileImageModelComponent.CLIP_G,
            MobileImageModelPolicy.detectComponent("model.safetensors", 1_390_000_000L),
        )
    }

    @Test
    fun standaloneQ4FileIsNotACompletePack() {
        val root = createTempDir(prefix = "q4-pack-")
        try {
            val diffusion = File(root, "WAI-illustrious-SDXL-v170-Q4_K_M.gguf").apply {
                writeBytes(byteArrayOf(1))
            }
            assertNotNull(MobileImageModelPolicy.validationError(diffusion))

            File(root, MobileImageModelPolicy.CLIP_L_FILE).writeBytes(byteArrayOf(1))
            File(root, MobileImageModelPolicy.CLIP_G_FILE).writeBytes(byteArrayOf(1))
            File(root, MobileImageModelPolicy.VAE_FILE).writeBytes(byteArrayOf(1))
            assertNull(MobileImageModelPolicy.validationError(diffusion))
        } finally {
            root.deleteRecursively()
        }
    }
}
