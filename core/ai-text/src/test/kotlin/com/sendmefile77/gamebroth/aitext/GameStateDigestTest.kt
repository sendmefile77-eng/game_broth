package com.sendmefile77.gamebroth.aitext

import com.sendmefile77.gamebroth.model.GameState
import com.sendmefile77.gamebroth.model.StaffMember
import com.sendmefile77.gamebroth.model.StaffStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateDigestTest {
    @Test
    fun digestIncludesStaffStatusAndLoyalty() {
        val state = GameState.newGame(42).copy(
            staff = listOf(
                StaffMember(
                    id = "s1",
                    name = "Мира",
                    species = "человек",
                    ageYears = 26,
                    loyalty = 12,
                    status = StaffStatus.LEFT,
                ),
            ),
        )

        val digest = GameStateDigest.from(state)
        assertTrue(digest.contains("status=LEFT"))
        assertTrue(digest.contains("loyalty=12"))
        assertTrue(digest.contains("Мира"))
    }
}
