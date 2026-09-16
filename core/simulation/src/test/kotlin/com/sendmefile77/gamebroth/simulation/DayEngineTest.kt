package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.GameState
import com.sendmefile77.gamebroth.model.SkillProgress
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.StaffStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayEngineTest {
    @Test
    fun sameStateProducesSameDayResult() {
        val state = GameState.newGame(42).copy(
            staff = listOf(
                StaffMember(
                    id = "s1",
                    name = "Мира",
                    species = "человек",
                    ageYears = 24,
                    skills = mapOf("hospitality" to SkillProgress("hospitality", level = 2)),
                ),
            ),
        )
        val engine = DayEngine()
        assertEquals(engine.advanceDay(state), engine.advanceDay(state))
    }

    @Test
    fun advancingDayNeverCreatesNegativeTreasury() {
        val result = DayEngine().advanceDay(GameState.newGame(1).copy(
            establishment = GameState.newGame(1).establishment.copy(treasury = 0),
        ))
        assertTrue(result.state.establishment.treasury >= 0)
        assertTrue(result.state.establishment.debt > 0)
        assertEquals(2, result.state.currentDay)
    }

    @Test
    fun workingDayCreatesDetailedDiaryFromFacts() {
        val state = GameState.newGame(77).copy(
            staff = listOf(
                StaffMember(
                    id = "s-diary",
                    name = "Ори",
                    species = "меднокровная кочевница",
                    ageYears = 27,
                    skills = mapOf("hospitality" to SkillProgress("hospitality", level = 2)),
                ),
            ),
        )
        val result = DayEngine().advanceDay(state)
        val diary = result.memories.first { it.staffId == "s-diary" && it.category == "diary" }.summary
        assertTrue(diary.length > 180)
        assertTrue(diary.contains("галеонов"))
        assertTrue(diary.contains("усталость"))
    }

    @Test
    fun plannedRestRecoversAndCreatesNoClients() {
        val state = GameState.newGame(88).copy(
            staff = listOf(
                StaffMember(
                    id = "s-rest",
                    name = "Немея",
                    species = "болотная лунница",
                    ageYears = 25,
                    fatigue = 80,
                    stress = 60,
                    health = 90,
                    status = StaffStatus.RESTING,
                ),
            ),
        )
        val result = DayEngine().advanceDay(state)
        val member = result.state.staff.single()
        val report = result.report.staffReports.single()
        assertTrue(member.fatigue < 80)
        assertTrue(member.stress < 60)
        assertTrue(member.health > 90)
        assertEquals(StaffStatus.AVAILABLE, member.status)
        assertTrue(report.encounters.isEmpty())
        assertTrue(report.incident.orEmpty().contains("отдых"))
    }

    @Test
    fun plannedTrainingImprovesSkillAndCostsMoney() {
        val state = GameState.newGame(99).copy(
            establishment = GameState.newGame(99).establishment.copy(treasury = 30),
            staff = listOf(
                StaffMember(
                    id = "s-train",
                    name = "Ори",
                    species = "меднокровная кочевница",
                    ageYears = 27,
                    status = StaffStatus.TRAINING,
                    skills = mapOf(
                        "hospitality" to SkillProgress("hospitality", level = 1, xp = 0),
                        "social" to SkillProgress("social", level = 3, xp = 0),
                    ),
                ),
            ),
        )
        val result = DayEngine().advanceDay(state)
        val member = result.state.staff.single()
        val report = result.report.staffReports.single()
        val hospitality = member.skills.getValue("hospitality")
        assertTrue(hospitality.xp > 0 || hospitality.level > 1)
        assertEquals(StaffStatus.AVAILABLE, member.status)
        assertTrue(report.encounters.isEmpty())
        assertTrue(report.incident.orEmpty().contains("обучение"))
        assertTrue(result.ledger.any { it.code == "TRAINING:s-train" && it.amount == -2L })
        assertTrue(result.report.upkeep >= 7L)
    }
}
