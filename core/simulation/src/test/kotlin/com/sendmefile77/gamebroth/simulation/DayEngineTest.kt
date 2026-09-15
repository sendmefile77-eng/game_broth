package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.GameState
import com.sendmefile77.gamebroth.model.SkillProgress
import com.sendmefile77.gamebroth.model.StaffMember
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
}
