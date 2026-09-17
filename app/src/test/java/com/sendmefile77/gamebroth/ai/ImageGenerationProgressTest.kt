package com.sendmefile77.gamebroth.ai

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationProgressTest {
    @After
    fun reset() = ImageGenerationProgressStore.reset()

    @Test
    fun tensorLoadingCounterIsNeverPresentedAsDiffusion() {
        ImageGenerationProgressStore.begin("Сена", itemIndex = 1, itemTotal = 3, width = 704, height = 1024, seed = 42)
        ImageGenerationProgressStore.loading(step = 374, total = 374)

        val loading = ImageGenerationProgressStore.state.value
        assertEquals(ImageGenerationStage.LOADING_MODEL, loading.stage)
        assertEquals("Тензор 374 из 374", loading.counterText)
        assertFalse(loading.counterText.orEmpty().startsWith("Шаг"))
        assertEquals(1f, loading.fraction)
        assertEquals("Сена · портрет 1 из 3", loading.subjectText)

        ImageGenerationProgressStore.diffusion(step = 7, totalSteps = 20, width = 704, height = 1024, seed = 42)
        val diffusion = ImageGenerationProgressStore.state.value
        assertEquals(ImageGenerationStage.DIFFUSION, diffusion.stage)
        assertEquals("Этап 7 из 20", diffusion.counterText)
        assertEquals(0.35f, diffusion.fraction)
    }

    @Test
    fun completeIsSeparateFromPngCreationSavingAndAttachment() {
        ImageGenerationProgressStore.begin("Сена")
        ImageGenerationProgressStore.encoding(704, 1024, 42)
        assertTrue(ImageGenerationProgressStore.state.value.running)
        assertNull(ImageGenerationProgressStore.state.value.fraction)

        ImageGenerationProgressStore.saving()
        assertEquals(ImageGenerationStage.SAVING_FILE, ImageGenerationProgressStore.state.value.stage)
        assertTrue(ImageGenerationProgressStore.state.value.running)

        ImageGenerationProgressStore.attaching()
        assertEquals(ImageGenerationStage.ATTACHING_ENTITY, ImageGenerationProgressStore.state.value.stage)
        assertTrue(ImageGenerationProgressStore.state.value.running)

        ImageGenerationProgressStore.complete()
        assertEquals(ImageGenerationStage.COMPLETE, ImageGenerationProgressStore.state.value.stage)
        assertFalse(ImageGenerationProgressStore.state.value.running)
        assertNull(ImageGenerationProgressStore.state.value.fraction)
    }

    @Test
    fun failureKeepsTechnicalDetailForDiagnostics() {
        ImageGenerationProgressStore.begin("Сена")
        ImageGenerationProgressStore.failed("Не удалось создать портрет", "generate_image failed: VAE decode failed")

        val failed = ImageGenerationProgressStore.state.value
        assertEquals(ImageGenerationStage.FAILED, failed.stage)
        assertEquals("Не удалось создать портрет", failed.message)
        assertEquals("generate_image failed: VAE decode failed", failed.technicalDetail)
        assertFalse(failed.running)
    }
}
