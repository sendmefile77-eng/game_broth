package com.sendmefile77.gamebroth.adultcontracts

class FallbackAdultSceneProvider : AdultSceneProvider {
    override suspend fun resolve(request: AdultSceneRequest): AdultSceneResult {
        val intensity = when {
            "high_intensity" in request.tags -> 3
            "low_intensity" in request.tags -> 1
            else -> 2
        }
        return AdultSceneResult(
            sceneId = request.sceneId,
            summaryTag = "consensual_private_scene",
            eventTags = request.tags + setOf("adult_only", "consent_confirmed", "fallback_provider"),
            proposedEffects = request.participants.flatMap { p ->
                listOf(
                    AdultMechanicalEffect(AdultEffectKind.SKILL_XP, p.staffId, "intimacy", 3 * intensity),
                    AdultMechanicalEffect(AdultEffectKind.FATIGUE, p.staffId, null, 2 * intensity),
                )
            },
        )
    }
}
