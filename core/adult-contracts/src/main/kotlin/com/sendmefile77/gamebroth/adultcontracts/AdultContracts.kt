package com.sendmefile77.gamebroth.adultcontracts

/**
 * Boundary for an optional mature-content implementation. The base game never depends on its text
 * or image details. This contract deliberately carries IDs/tags/effects rather than explicit prose.
 */
data class AdultParticipantRef(
    val staffId: String,
    val ageYears: Int,
    val consentConfirmed: Boolean,
) {
    init {
        require(staffId.isNotBlank())
        require(ageYears >= 18) { "Mature-content modules may only receive adult participants." }
        require(consentConfirmed) { "Mature-content modules require explicit participant consent." }
    }
}

data class AdultSceneRequest(
    val sceneId: String,
    val worldDay: Int,
    val participants: List<AdultParticipantRef>,
    val contextDigest: String,
    val tags: Set<String> = emptySet(),
) {
    init {
        require(sceneId.isNotBlank())
        require(worldDay >= 1)
        require(participants.isNotEmpty())
        require(participants.map { it.staffId }.distinct().size == participants.size)
    }
}

enum class AdultEffectKind {
    STAFF_XP,
    SKILL_XP,
    FATIGUE,
    STRESS,
    HEALTH,
    RELATIONSHIP,
    TREASURY,
    REPUTATION,
}

data class AdultMechanicalEffect(
    val kind: AdultEffectKind,
    val targetId: String?,
    val key: String? = null,
    val delta: Int,
)

data class AdultSceneResult(
    val sceneId: String,
    val summaryTag: String,
    val eventTags: Set<String> = emptySet(),
    val proposedEffects: List<AdultMechanicalEffect> = emptyList(),
)

interface AdultSceneProvider {
    suspend fun resolve(request: AdultSceneRequest): AdultSceneResult
}
