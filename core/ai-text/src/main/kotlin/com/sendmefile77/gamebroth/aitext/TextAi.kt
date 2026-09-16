package com.sendmefile77.gamebroth.aitext

import com.sendmefile77.gamebroth.model.*

data class TextAiStatus(val available: Boolean, val model: String? = null, val detail: String? = null)
data class TextGenerationRequest(
    val systemPrompt: String,
    val stateDigest: String,
    val playerAction: String,
    val recentEvents: List<WorldEvent> = emptyList(),
    val maxTokens: Int = 600,
    val temperature: Double = 0.65,
)
data class TextGenerationResult(val content: String, val model: String, val elapsedMs: Long)
interface TextNarrator { suspend fun status(force: Boolean = false): TextAiStatus; suspend fun generate(request: TextGenerationRequest): TextGenerationResult? }

object GameStateDigest {
    fun from(state: GameState): String = buildString {
        append("day=").append(state.currentDay)
        append("; establishment=").append(state.establishment.name)
        append("; treasury=").append(state.establishment.treasury)
        append("; debt=").append(state.establishment.debt)
        append("; reputation=").append(state.establishment.publicReputation)
        append("; heat=").append(state.establishment.heat)
        append("; luxury=").append(state.establishment.luxury)
        append("; secrecy=").append(state.establishment.secrecy)
        append("; staff=").append(state.staff.joinToString(" | ") { m ->
            val limits = m.preferences.filterValues { it == PreferenceStance.HARD_LIMIT }.keys.sorted().joinToString(",")
            val likes = m.preferences.filterValues { it == PreferenceStance.ENJOY }.keys.sorted().joinToString(",")
            val goal = state.staffGoals.firstOrNull { it.staffId == m.id && it.status == StaffGoalStatus.ACTIVE }
            "${m.id}:${m.name},status=${m.status.name},lvl=${m.level},hp=${m.health},fatigue=${m.fatigue},stress=${m.stress},loyalty=${m.loyalty},money=${m.personalMoney},goal=[${goal?.title.orEmpty()} ${goal?.progress ?: 0}/${goal?.target ?: 0}],likes=[$likes],limits=[$limits],items=[${m.inventory.takeLast(5).joinToString { it.name }}]"
        })
        append("; relations=").append(state.staffRelations.joinToString(" | ") { r ->
            "${r.firstStaffId}<->${r.secondStaffId}:affinity=${r.affinity},tension=${r.tension}"
        })
    }
}
