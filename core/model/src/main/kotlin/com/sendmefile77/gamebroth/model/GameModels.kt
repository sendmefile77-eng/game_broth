package com.sendmefile77.gamebroth.model

import java.util.UUID

enum class StaffStatus { AVAILABLE, WORKING, RESTING, INJURED, LEFT }
enum class PreferenceStance { ENJOY, ACCEPT, AVOID, HARD_LIMIT }

data class SkillProgress(val code: String, val level: Int = 1, val xp: Int = 0) {
    init { require(code.isNotBlank()); require(level in 1..15); require(xp >= 0) }
}

data class InventoryItem(val id: String, val name: String, val quantity: Int = 1, val tags: Set<String> = emptySet()) {
    init { require(id.isNotBlank()); require(name.isNotBlank()); require(quantity > 0) }
}

data class StaffMember(
    val id: String,
    val name: String,
    val species: String,
    val ageYears: Int,
    val level: Int = 1,
    val xp: Int = 0,
    val personalMoney: Long = 0,
    val loyalty: Int = 50,
    val stress: Int = 0,
    val fatigue: Int = 0,
    val health: Int = 100,
    val reputation: Int = 0,
    val status: StaffStatus = StaffStatus.AVAILABLE,
    val traits: Set<String> = emptySet(),
    val skills: Map<String, SkillProgress> = emptyMap(),
    val preferences: Map<String, PreferenceStance> = emptyMap(),
    val inventory: List<InventoryItem> = emptyList(),
) {
    init {
        require(id.isNotBlank()); require(name.isNotBlank()); require(species.isNotBlank())
        require(ageYears >= 18); require(level >= 1); require(xp >= 0); require(personalMoney >= 0)
        require(loyalty in 0..100); require(stress in 0..100); require(fatigue in 0..100); require(health in 0..100)
        require(reputation in -100..100); require(skills.values.all { it.code in skills.keys })
    }
}

data class EstablishmentState(
    val id: String = "main",
    val name: String = "Ржавый Фаллос",
    val level: Int = 1,
    val treasury: Long = 12,
    val debt: Long = 0,
    val publicReputation: Int = 0,
    val heat: Int = 0,
    val luxury: Int = 0,
    val secrecy: Int = 0,
    val arcane: Int = 0,
    val politicalInfluence: Int = 0,
) {
    init {
        require(level >= 1); require(treasury >= 0); require(debt >= 0); require(publicReputation in -100..100)
        require(heat in 0..100); require(luxury in 0..100); require(secrecy in 0..100); require(arcane in 0..100); require(politicalInfluence in 0..100)
    }
}

enum class QuestStatus { ACTIVE, COMPLETED, FAILED, LOCKED }
data class QuestState(val id: String, val title: String, val summary: String, val status: QuestStatus = QuestStatus.ACTIVE, val progress: Int = 0, val target: Int = 1) {
    init { require(id.isNotBlank()); require(title.isNotBlank()); require(target > 0); require(progress >= 0) }
}
data class FactionState(val id: String, val name: String, val relation: Int = 0, val influence: Int = 0, val attention: Int = 0) {
    init { require(id.isNotBlank()); require(name.isNotBlank()); require(relation in -100..100); require(influence in 0..100); require(attention in 0..100) }
}
data class ArtifactState(val id: String, val name: String, val charges: Int = 0, val maxCharges: Int = 0, val tags: Set<String> = emptySet()) {
    init { require(id.isNotBlank()); require(name.isNotBlank()); require(charges >= 0); require(maxCharges >= 0); require(charges <= maxCharges || maxCharges == 0) }
}
data class SecretState(val id: String, val title: String, val ownerNpcId: String? = null, val leverage: Int = 0, val exposed: Boolean = false) {
    init { require(id.isNotBlank()); require(title.isNotBlank()); require(leverage in 0..100) }
}

data class GameState(
    val schemaVersion: Int = 3,
    val worldSeed: Long,
    val currentDay: Int = 1,
    val establishment: EstablishmentState = EstablishmentState(),
    val staff: List<StaffMember> = emptyList(),
    val quests: List<QuestState> = emptyList(),
    val factions: List<FactionState> = emptyList(),
    val artifacts: List<ArtifactState> = emptyList(),
    val secrets: List<SecretState> = emptyList(),
) {
    init { require(currentDay >= 1); require(staff.map { it.id }.distinct().size == staff.size) }
    companion object { fun newGame(seed: Long = System.currentTimeMillis()) = GameState(worldSeed = seed) }
}

data class ClientProfile(
    val id: String,
    val displayName: String,
    val archetype: String,
    val ageYears: Int,
    val wealth: Int,
    val patience: Int,
    val discretion: Int,
    val interests: Set<String> = emptySet(),
) {
    init { require(ageYears >= 18); require(wealth in 1..100); require(patience in 1..100); require(discretion in 1..100) }
}

enum class EncounterOutcome { EXCELLENT, GOOD, ROUTINE, AWKWARD, REFUSED, INCIDENT }

data class WorkEncounter(
    val id: String,
    val day: Int,
    val staffId: String,
    val client: ClientProfile,
    val serviceCode: String,
    val outcome: EncounterOutcome,
    val summary: String,
    val grossRevenue: Long,
    val staffCut: Long,
    val businessCut: Long,
    val trainedSkill: String,
    val skillXp: Int,
    val fatigueDelta: Int,
    val stressDelta: Int,
    val healthDelta: Int,
    val incident: String? = null,
) {
    init { require(staffCut + businessCut == grossRevenue); require(grossRevenue >= 0); require(skillXp >= 0) }
}

data class PersonalPurchase(val item: InventoryItem, val price: Long, val reason: String) { init { require(price > 0); require(reason.isNotBlank()) } }

data class StaffDayReport(
    val day: Int,
    val staffId: String,
    val staffName: String,
    val levelBefore: Int,
    val levelAfter: Int,
    val encounters: List<WorkEncounter>,
    val businessRevenue: Long,
    val personalRevenue: Long,
    val purchase: PersonalPurchase? = null,
    val incident: String? = null,
) {
    init { require(day >= 1); require(staffId.isNotBlank()); require(staffName.isNotBlank()); require(levelBefore >= 1); require(levelAfter >= 1); require(businessRevenue >= 0); require(personalRevenue >= 0); require(encounters.all { it.day == day && it.staffId == staffId }) }
}

data class DailyReport(
    val day: Int,
    val staffReports: List<StaffDayReport>,
    val grossRevenue: Long,
    val upkeep: Long,
    val treasuryAfter: Long,
    val debtDelta: Long,
    val createdAtEpochMs: Long,
) {
    init { require(day >= 1); require(grossRevenue >= 0); require(upkeep >= 0); require(treasuryAfter >= 0); require(debtDelta >= 0); require(staffReports.all { it.day == day }) }
}

data class StaffMemory(
    val id: String = UUID.randomUUID().toString(),
    val staffId: String,
    val day: Int,
    val category: String,
    val summary: String,
    val importance: Int = 1,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) { init { require(id.isNotBlank()); require(staffId.isNotBlank()); require(day >= 1); require(category.isNotBlank()); require(summary.isNotBlank()); require(importance in 1..5) } }

data class WorldEvent(
    val id: String = UUID.randomUUID().toString(),
    val day: Int,
    val type: String,
    val summary: String,
    val payload: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
) { init { require(id.isNotBlank()); require(day >= 1); require(type.isNotBlank()); require(summary.isNotBlank()) } }
