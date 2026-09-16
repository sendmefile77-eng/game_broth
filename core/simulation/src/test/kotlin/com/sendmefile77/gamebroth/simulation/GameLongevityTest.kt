package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLongevityTest {
    @Test fun thirtyDaysRemainValidAndDeterministic() {
        val first = runThirtyDays(seed = 20260916L)
        val repeat = runThirtyDays(seed = 20260916L)

        assertEquals(first, repeat)
        assertEquals(31, first.currentDay)
        assertTrue(first.schemaVersion >= 6)
        assertTrue(first.establishment.treasury >= 0)
        assertTrue(first.establishment.debt >= 0)
        assertTrue(first.establishment.heat in 0..100)
        assertTrue(first.staff.all { it.health in 0..100 && it.fatigue in 0..100 && it.stress in 0..100 && it.loyalty in 0..100 })
        assertTrue(first.staffRelations.all { it.affinity in -100..100 && it.tension in 0..100 })
        assertTrue(first.staffGoals.all { it.progress >= 0 && it.target > 0 })
    }

    private fun runThirtyDays(seed: Long): GameState {
        val dayEngine = DayEngine()
        val life = StaffLifeEngine()
        val management = EstablishmentEngine()
        var state = GameState(
            worldSeed = seed,
            establishment = EstablishmentState(treasury = 45, luxury = 10, secrecy = 10),
            staff = listOf(
                staff("a", "Ада", 82),
                staff("b", "Беа", 78),
                staff("c", "Сира", 75),
            ),
        )
        var knownRequests = emptyList<StaffRequest>()

        repeat(30) { index ->
            if (state.staff.none { it.status != StaffStatus.LEFT }) return@repeat

            if (index == 8) state = management.setPricing(state, PricingPolicy.PREMIUM).state
            if (index == 14) state = management.setWorkload(state, WorkloadPolicy.GENTLE).state
            if (index == 20 && state.establishment.heat > 0 && state.establishment.treasury >= 4) {
                state = runCatching { management.layLow(state).state }.getOrDefault(state)
            }

            val core = dayEngine.advanceDay(state)
            val living = life.afterDay(core.state, core.report, knownRequests)
            state = living.state
            knownRequests = living.requestUpdates.filter { it.status == StaffRequestStatus.PENDING }

            // Simulate a reasonable owner: answer every request before the next close.
            knownRequests.toList().forEach { request ->
                val accept = request.cost <= state.establishment.treasury && request.kind != StaffRequestKind.BONUS
                val resolution = life.resolve(state, request, accept)
                state = resolution.state
                knownRequests = knownRequests.map { if (it.id == request.id) resolution.request else it }
                    .filter { it.status == StaffRequestStatus.PENDING }
            }
        }
        return state
    }

    private fun staff(id: String, name: String, loyalty: Int) = StaffMember(
        id = id,
        name = name,
        species = if (id == "c") "elf" else "human",
        ageYears = 25 + id.first().code % 4,
        loyalty = loyalty,
        traits = if (id == "a" || id == "b") setOf("calm") else setOf("ambitious"),
        skills = mapOf(
            "hospitality" to SkillProgress("hospitality", 2, 0),
            "social" to SkillProgress("social", 2, 0),
            "bodywork" to SkillProgress("bodywork", 2, 0),
            "roleplay" to SkillProgress("roleplay", 1, 0),
            "intimacy" to SkillProgress("intimacy", 2, 0),
            "arcane" to SkillProgress("arcane", 1, 0),
        ),
        preferences = mapOf(
            "conversation" to PreferenceStance.ENJOY,
            "massage" to PreferenceStance.ACCEPT,
            "roleplay" to PreferenceStance.ACCEPT,
            "private_intimacy" to PreferenceStance.ACCEPT,
            "arcane_fantasy" to if (id == "c") PreferenceStance.ENJOY else PreferenceStance.AVOID,
        ),
    )
}
