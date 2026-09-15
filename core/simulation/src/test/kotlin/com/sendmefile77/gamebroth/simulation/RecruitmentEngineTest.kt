package com.sendmefile77.gamebroth.simulation

import com.sendmefile77.gamebroth.model.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecruitmentEngineTest {
    private val engine = RecruitmentEngine()

    @Test
    fun offersAreDeterministicAndHaveThreeOptions() {
        val state = GameState.newGame(99)
        val first = engine.locations(state)
        val second = engine.locations(state)
        assertEquals(first, second)
        assertEquals(3, first.size)
        assertEquals(3, first.map { it.id }.distinct().size)
    }

    @Test
    fun hiringPersistsAsStateAndCostsMoney() {
        val state = GameState.newGame(99)
        val location = engine.locations(state).first()
        val candidate = engine.candidates(state, location).first()
        val result = engine.hire(state, candidate)
        assertEquals(1, result.state.staff.size)
        assertEquals(state.establishment.treasury - candidate.signingFee, result.state.establishment.treasury)
        assertTrue(result.state.staff.first().ageYears >= 18)
    }
}
