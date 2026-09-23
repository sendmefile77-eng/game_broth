package com.sendmefile77.gamebroth.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDreamDiagnosticsTest {
    @Test
    fun retainsQnnFailureInsteadOfPromptAndLaterStageLogs() {
        val lines = listOf(
            "Prompt: private game narrative with QNN error model",
            "[lowram] SDXL UNET loaded",
            "[ ERROR ] sdxl unet graph execution failed: status=14007",
            "sdxl unet input[0] name=sample dims=1x4x128x128 dtype=1032",
            "[lowram] SDXL UNET released",
        )
        val diagnostic = LocalDreamDiagnostics.summary(lines)
        assertTrue(diagnostic.contains("status=14007"))
        assertFalse(diagnostic.contains("private game narrative"))
        assertFalse(diagnostic.contains("UNET released"))
    }

    @Test
    fun fallsBackToStageWhenNoNativeErrorIsAvailable() {
        assertEquals(
            "[lowram] SDXL UNET loaded",
            LocalDreamDiagnostics.summary(listOf("Prompt: secret", "[lowram] SDXL UNET loaded")),
        )
    }
}
