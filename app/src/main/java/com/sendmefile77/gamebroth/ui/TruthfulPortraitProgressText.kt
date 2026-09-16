package com.sendmefile77.gamebroth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sendmefile77.gamebroth.ai.ImageGenerationProgressStore
import com.sendmefile77.gamebroth.ai.ImageGenerationStage

/** Progress panel used only by the candidate that is currently being rendered. */
@Composable
internal fun PortraitGenerationProgress(
    candidateName: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress by ImageGenerationProgressStore.state.collectAsState()
    val subject = progress.subjectText ?: "$candidateName · создаём портрет"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.76f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(subject, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Text(progress.message.ifBlank { "Запускаем генератор…" }, color = color, fontSize = 12.sp)

        val fraction = progress.fraction
        if (progress.stage == ImageGenerationStage.FAILED) {
            Text("Подробность записана в диагностике.", color = color, fontSize = 11.sp)
        } else if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            progress.counterText?.let { counter ->
                Text(
                    counter + if (progress.width > 0 && progress.height > 0) " · ${progress.width}×${progress.height}" else "",
                    color = color,
                    fontSize = 11.sp,
                )
            }
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text("Процент для этого этапа движок не сообщает.", color = color, fontSize = 11.sp)
        }
    }
}
