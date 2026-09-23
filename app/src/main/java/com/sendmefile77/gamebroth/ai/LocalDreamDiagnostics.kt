package com.sendmefile77.gamebroth.ai

/** Selects native failures without copying the user's image prompt into the UI. */
internal object LocalDreamDiagnostics {
    private val errorLine = Regex("(?i)(\\[\\s*error\\s*]|\\bgraph execution failed\\b|\\bfailed to (load|create|execute)\\b|QnnDsp\\s*<E>|rpc transport)")
    private val nativeContext = Regex(
        "(?i)(qnn|htp|hexagon|dsp|rpc|spill.fill|unet|graph|context|memory|alloc|backend|skel)",
    )
    private val stageLine = Regex("(?i)(\\[lowram]|sdxl.*(loaded|released)|spill.fill)")

    fun isRelevant(line: String): Boolean =
        !line.contains("Prompt:", ignoreCase = true) &&
            ((errorLine.containsMatchIn(line) && nativeContext.containsMatchIn(line)) ||
                stageLine.containsMatchIn(line))

    fun summary(lines: Collection<String>): String {
        val relevant = lines.filter(::isRelevant)
        // Keep the actual graphExecute status even if later stage logs follow it.
        val failures = relevant.filter(errorLine::containsMatchIn)
        val selected = if (failures.isNotEmpty()) failures.takeLast(5) else relevant.takeLast(3)
        return selected.joinToString(" · ") { it.trim().take(300) }.take(1_500)
    }
}
