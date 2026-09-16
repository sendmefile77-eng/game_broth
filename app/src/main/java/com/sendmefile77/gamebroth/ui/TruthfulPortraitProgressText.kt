package com.sendmefile77.gamebroth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sendmefile77.gamebroth.ai.ImageBackendConfig
import com.sendmefile77.gamebroth.ai.ImageBackendMode
import com.sendmefile77.gamebroth.ai.ImageGenerationProgressStore
import com.sendmefile77.gamebroth.ai.ImageGenerationStage
import kotlin.math.roundToInt

/**
 * Narrow overload used by the two recruitment portrait placeholders in GameUi.
 *
 * It deliberately reports only progress that the active backend can actually prove:
 * - EMBEDDED: exact denoising step/total from stable-diffusion.cpp native callback.
 * - LOCAL_DREAM: indeterminate bar, because the current HTTP contract does not expose a
 *   trustworthy step counter. We never invent a percentage from elapsed time.
 *
 * The normal Material3 Text implementation is preserved for every other call that happens to
 * match this overload.
 */
@Composable
internal fun Text(
    text: String,
    color: Color,
    modifier: Modifier,
) {
    val isPortraitPlaceholder = text == "создаём портрет…" || text == "создаём полный портрет…"
    if (!isPortraitPlaceholder) {
        androidx.compose.material3.Text(
            text = text,
            color = color,
            modifier = modifier,
        )
        return
    }

    val progress by ImageGenerationProgressStore.state.collectAsState()
    val embedded = ImageBackendConfig.mode == ImageBackendMode.EMBEDDED
    val exactFraction = if (embedded) progress.fraction else null
    val exactPercent = exactFraction?.let { (it * 100f).roundToInt().coerceIn(0, 100) }

    val status = when {
        !embedded -> "Local Dream создаёт портрет…"
        progress.stage == ImageGenerationStage.FAILED ->
            progress.message.ifBlank { "Генерация остановлена из-за ошибки" }
        exactFraction != null -> "Диффузия: $exactPercent%"
        progress.stage == ImageGenerationStage.COMPLETE ->
            // Generator has produced image bytes, but the card has not switched to the saved file
            // yet. Do not claim overall 100% before the UI can actually display that file.
            "Сохраняем готовый портрет…"
        progress.running && progress.message.isNotBlank() -> progress.message
        else -> "Запускаем встроенный генератор…"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        androidx.compose.material3.Text(
            text = status,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )

        if (exactFraction != null) {
            LinearProgressIndicator(
                progress = { exactFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            androidx.compose.material3.Text(
                text = "Шаг ${progress.step} из ${progress.totalSteps}" +
                    if (progress.width > 0 && progress.height > 0) " · ${progress.width}×${progress.height}" else "",
                color = color,
                fontSize = 12.sp,
            )
        } else if (progress.stage != ImageGenerationStage.FAILED) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            androidx.compose.material3.Text(
                text = if (embedded) {
                    "Точный процент появится только на реальных шагах diffusion."
                } else {
                    "Local Dream не сообщает точный шаг — процент не выдумываем."
                },
                color = color,
                fontSize = 11.sp,
            )
        }
    }
}
