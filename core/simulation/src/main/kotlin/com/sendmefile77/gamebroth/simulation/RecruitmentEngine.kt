package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import kotlin.random.Random

data class RecruitmentLocation(val id: String, val name: String, val district: String, val risk: Int, val entryCost: Long, val tags: Set<String>)
data class RecruitCandidate(
    val id: String,
    val name: String,
    val species: String,
    val ageYears: Int,
    val signingFee: Long,
    val traits: Set<String>,
    val skills: Map<String, SkillProgress>,
    val preferences: Map<String, PreferenceStance>,
    val startingLoyalty: Int,
    val profileHook: String,
)
data class HireResult(val state: GameState, val event: WorldEvent)

class RecruitmentEngine {
    fun locations(state: GameState): List<RecruitmentLocation> =
        LOCATION_POOL.shuffled(Random(state.worldSeed xor (state.currentDay.toLong() shl 17))).take(3)

    fun candidates(state: GameState, location: RecruitmentLocation): List<RecruitCandidate> {
        val random = Random(state.worldSeed xor location.id.hashCode().toLong() xor (state.currentDay.toLong() shl 29))
        return ARCHETYPES.shuffled(random).take(3).mapIndexed { index, a ->
            val social = random.nextInt(1,4)
            val hospitality = random.nextInt(1,4)
            val specialty = SPECIALTY.random(random)
            val specialtyLevel = random.nextInt(1,3)
            RecruitCandidate(
                id = "recruit-${state.worldSeed}-${state.currentDay}-${location.id}-$index",
                name = a.names.random(random),
                species = a.species,
                ageYears = a.age.random(random),
                signingFee = (3 + social + hospitality + specialtyLevel + location.risk / 25).toLong(),
                traits = a.traits + location.tags.shuffled(random).take(1),
                skills = mapOf(
                    "hospitality" to SkillProgress("hospitality", hospitality),
                    "social" to SkillProgress("social", social),
                    specialty to SkillProgress(specialty, specialtyLevel),
                ),
                preferences = preferences(random, specialty),
                startingLoyalty = random.nextInt(38,66),
                profileHook = a.hook,
            )
        }
    }

    fun hire(state: GameState, candidate: RecruitCandidate): HireResult {
        require(candidate.ageYears >= 18)
        require(state.staff.none { it.id == candidate.id })
        require(state.establishment.treasury >= candidate.signingFee) { "Not enough treasury" }
        val staff = StaffMember(
            id = candidate.id,
            name = candidate.name,
            species = candidate.species,
            ageYears = candidate.ageYears,
            loyalty = candidate.startingLoyalty,
            traits = candidate.traits,
            skills = candidate.skills,
            preferences = candidate.preferences,
        )
        val next = state.copy(
            schemaVersion = maxOf(state.schemaVersion, 3),
            establishment = state.establishment.copy(treasury = state.establishment.treasury - candidate.signingFee),
            staff = state.staff + staff,
        )
        val event = WorldEvent(
            id = "hire-${state.currentDay}-${candidate.id}",
            day = state.currentDay,
            type = "STAFF_HIRED",
            summary = "Нанята ${candidate.name} (${candidate.species}); расход ${candidate.signingFee}.",
            payload = "staffId=${candidate.id};fee=${candidate.signingFee}",
            createdAtEpochMs = 946684800000L + state.currentDay * 86_400_000L,
        )
        return HireResult(next, event)
    }

    private fun preferences(random: Random, specialty: String): Map<String, PreferenceStance> {
        val preferred = when (specialty) {
            "bodywork" -> "massage"
            "roleplay" -> "roleplay"
            "intimacy" -> "private_intimacy"
            "arcane" -> "arcane_fantasy"
            else -> "conversation"
        }
        val hard = SERVICES.filter { it != preferred }.random(random)
        return SERVICES.associateWith { code ->
            when {
                code == preferred -> PreferenceStance.ENJOY
                code == hard -> PreferenceStance.HARD_LIMIT
                random.nextInt(100) < 25 -> PreferenceStance.AVOID
                else -> PreferenceStance.ACCEPT
            }
        }
    }

    private data class Archetype(
        val species: String,
        val names: List<String>,
        val age: IntRange,
        val traits: Set<String>,
        val hook: String,
    )

    companion object {
        private val SERVICES = listOf("conversation","massage","roleplay","private_intimacy","arcane_fantasy")
        private val SPECIALTY = listOf("bodywork","roleplay","intimacy","arcane")

        // Entry fees stay zero until the city-economy layer can persist paid access and explain consequences.
        private val LOCATION_POOL = listOf(
            RecruitmentLocation("old_aqueduct","Ночная прачечная у Старого Акведука","Водяной квартал",18,0,setOf("слухи","бедняки")),
            RecruitmentLocation("seventh_mask","Подвальный театр «Седьмая Маска»","Квартал декораторов",27,0,setOf("артисты","маски")),
            RecruitmentLocation("lame_comet","Караванный двор «Хромая Комета»","Южные ворота",34,0,setOf("чужеземцы","караваны")),
            RecruitmentLocation("glass_market","Рынок битого стекла","Ремесленная дуга",43,0,setOf("контрабанда","ремесло")),
            RecruitmentLocation("mushroom_cellar","Грибные подвалы аптекарей","Нижний город",52,0,setOf("алхимия","редкости")),
            RecruitmentLocation("ink_docks","Чернильные доки переписчиков","Канал архивов",47,0,setOf("секреты","писцы")),
        )
        private val ARCHETYPES = listOf(
            Archetype("каменнокожая горянка",listOf("Рава","Ирра","Меви"),22..34,setOf("стойкая","прямолинейная"),"Бывшая грузчица каменоломни, хочет расплатиться с семейным долгом."),
            Archetype("болотная лунница",listOf("Сай","Немея","Тилла"),21..31,setOf("наблюдательная","суеверная"),"Хорошо читает людей, но избегает храмовых служителей."),
            Archetype("меднокровная кочевница",listOf("Яра","Сена","Ори"),23..36,setOf("смелая","азартная"),"Торгуется за каждую монету и хорошо помнит лица."),
            Archetype("речная амфибия",listOf("Лисса","Мора","Энни"),24..38,setOf("спокойная","скрытная"),"Работала проводницей по каналам и слышала слишком много разговоров."),
            Archetype("фарфоровая рождённая",listOf("Элиа","Соми","Виен"),25..41,setOf("невозмутимая","эстет"),"Коллекционирует украшения и замечает подделки."),
            Archetype("чернильная горожанка",listOf("Нова","Эсса","Лиор"),22..30,setOf("грамотная","саркастичная"),"Была переписчицей и узнаёт опасные письма."),
        )
    }
}
