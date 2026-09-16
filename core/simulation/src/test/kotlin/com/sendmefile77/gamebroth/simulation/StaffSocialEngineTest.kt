package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffSocialEngineTest {
    private val engine = StaffSocialEngine()

    @Test fun createsPersistentPairAndGoalsDeterministically() {
        val state = GameState(
            worldSeed = 77L,
            currentDay = 2,
            staff = listOf(
                staff("a", "Ада", traits = setOf("calm")),
                staff("b", "Беа", traits = setOf("calm")),
            ),
        )
        val report = report(day = 1, state.staff)
        val first = engine.afterDay(state, report)
        val repeat = engine.afterDay(state, report)

        assertEquals(first.state.staffRelations, repeat.state.staffRelations)
        assertEquals(first.state.staffGoals, repeat.state.staffGoals)
        assertEquals(1, first.state.staffRelations.size)
        assertEquals(2, first.state.staffGoals.count { it.status == StaffGoalStatus.ACTIVE })
        assertTrue(first.state.staffRelations.single().affinity > 0)
    }

    @Test fun completingGoalRewardsLoyaltyAndCreatesHistory() {
        val member = staff("a", "Ада", loyalty = 50, personalMoney = 12)
        val goal = StaffGoal(
            id = "goal-old",
            staffId = member.id,
            kind = StaffGoalKind.EARN_MONEY,
            title = "накопить 10 личных галеонов",
            target = 10,
            progress = 4,
            createdDay = 1,
            deadlineDay = 5,
        )
        val state = GameState(worldSeed = 11L, currentDay = 2, staff = listOf(member), staffGoals = listOf(goal))
        val result = engine.afterDay(state, report(1, listOf(member)))

        assertTrue(result.state.staffGoals.any { it.id == "goal-old" && it.status == StaffGoalStatus.COMPLETED })
        assertEquals(52, result.state.staff.single().loyalty)
        assertTrue(result.events.any { it.type == "STAFF_GOAL_COMPLETED" })
        assertTrue(result.state.staffGoals.any { it.status == StaffGoalStatus.ACTIVE })
    }

    @Test fun expiredGoalHasConsequenceAndIsReplaced() {
        val member = staff("a", "Ада", loyalty = 50, personalMoney = 0)
        val goal = StaffGoal(
            id = "goal-expired",
            staffId = member.id,
            kind = StaffGoalKind.EARN_MONEY,
            title = "накопить 99",
            target = 99,
            progress = 0,
            createdDay = 1,
            deadlineDay = 1,
        )
        val state = GameState(worldSeed = 12L, currentDay = 3, staff = listOf(member), staffGoals = listOf(goal))
        val result = engine.afterDay(state, report(2, listOf(member)))

        val after = result.state.staff.single()
        assertEquals(49, after.loyalty)
        assertEquals(2, after.stress)
        assertTrue(result.state.staffGoals.any { it.id == "goal-expired" && it.status == StaffGoalStatus.FAILED })
        assertTrue(result.state.staffGoals.any { it.status == StaffGoalStatus.ACTIVE })
    }

    private fun staff(
        id: String,
        name: String,
        loyalty: Int = 50,
        personalMoney: Long = 0,
        traits: Set<String> = emptySet(),
    ) = StaffMember(
        id = id,
        name = name,
        species = "human",
        ageYears = 25,
        loyalty = loyalty,
        personalMoney = personalMoney,
        traits = traits,
        skills = mapOf("hospitality" to SkillProgress("hospitality", 1, 0)),
    )

    private fun report(day: Int, staff: List<StaffMember>) = DailyReport(
        day = day,
        staffReports = staff.map { member ->
            StaffDayReport(day, member.id, member.name, member.level, member.level, emptyList(), 0, 0, incident = "План дня: отдых.")
        },
        grossRevenue = 0,
        upkeep = 0,
        treasuryAfter = 10,
        debtDelta = 0,
        createdAtEpochMs = 1_800_000_000_000L + day,
    )
}
